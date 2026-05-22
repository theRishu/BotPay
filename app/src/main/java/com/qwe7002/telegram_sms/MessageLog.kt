package com.qwe7002.telegram_sms

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MessageLog {
    private const val KEY = "message_log"
    private const val MAX = 500

    data class Entry(
        val time: Long,
        val sender: String,
        val text: String,
        val forwarded: Boolean,
        val type: String,   // "credited", "debited", or ""
        val amount: String, // e.g. "50.54"
        val utr: String     // e.g. "330141520382" (unique transaction ref)
    )

    fun add(context: Context, entry: Entry) {
        val prefs = context.getSharedPreferences("botpay", Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
        val obj = JSONObject().apply {
            put("time",      entry.time)
            put("sender",    entry.sender)
            put("text",      entry.text)
            put("forwarded", entry.forwarded)
            put("type",      entry.type)
            put("amount",    entry.amount)
            put("utr",       entry.utr)
        }
        val newArr = JSONArray()
        newArr.put(obj)
        for (i in 0 until minOf(arr.length(), MAX - 1)) newArr.put(arr.getJSONObject(i))
        prefs.edit().putString(KEY, newArr.toString()).apply()
    }

    fun getAll(context: Context): List<Entry> {
        val prefs = context.getSharedPreferences("botpay", Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { return emptyList() }
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Entry(
                time      = obj.getLong("time"),
                sender    = obj.getString("sender"),
                text      = obj.getString("text"),
                forwarded = obj.getBoolean("forwarded"),
                type      = obj.optString("type",   ""),
                amount    = obj.optString("amount", ""),
                utr       = obj.optString("utr",    "")
            )
        }
    }

    // Returns true if a UTR was already forwarded — prevents duplicate forwards
    fun isUTRDuplicate(context: Context, utr: String): Boolean {
        if (utr.isEmpty()) return false
        return getAll(context).any { it.utr == utr && it.forwarded }
    }

    fun markForwarded(context: Context, time: Long) {
        val prefs = context.getSharedPreferences("botpay", Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { return }
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getLong("time") == time) {
                obj.put("forwarded", true)
                val text = obj.optString("text", "")
                if (obj.optString("type",   "").isEmpty()) obj.put("type",   deriveType(text))
                if (obj.optString("amount", "").isEmpty()) obj.put("amount", extractAmount(text))
                if (obj.optString("utr",    "").isEmpty()) obj.put("utr",    extractUTR(text))
            }
            newArr.put(obj)
        }
        prefs.edit().putString(KEY, newArr.toString()).apply()
    }

    fun remove(context: Context, time: Long) {
        val prefs = context.getSharedPreferences("botpay", Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { return }
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getLong("time") != time) newArr.put(obj)
        }
        prefs.edit().putString(KEY, newArr.toString()).apply()
    }

    // One-time migration: patch entries missing type, amount, or utr
    fun migrate(context: Context) {
        val prefs = context.getSharedPreferences("botpay", Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { return }
        var changed = false
        for (i in 0 until arr.length()) {
            val obj  = arr.getJSONObject(i)
            val text = obj.optString("text", "")
            if (obj.optString("type",   "").isEmpty()) { obj.put("type",   deriveType(text));    changed = true }
            if (obj.optString("amount", "").isEmpty()) { obj.put("amount", extractAmount(text)); changed = true }
            if (obj.optString("utr",    "").isEmpty()) { obj.put("utr",    extractUTR(text));    changed = true }
        }
        if (changed) prefs.edit().putString(KEY, arr.toString()).apply()
    }

    // Force re-derive type, amount, utr for ALL entries from raw SMS text
    fun recalculate(context: Context) {
        val prefs = context.getSharedPreferences("botpay", Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { return }
        for (i in 0 until arr.length()) {
            val obj  = arr.getJSONObject(i)
            val text = obj.optString("text", "")
            obj.put("type",   deriveType(text))
            obj.put("amount", extractAmount(text))
            obj.put("utr",    extractUTR(text))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    // Export all forwarded entries as a CSV string
    fun exportCSV(context: Context): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sb = StringBuilder("Date,Time,Sender,UTR,Type,Amount\n")
        getAll(context).filter { it.forwarded }.reversed().forEach { e ->
            val d = Date(e.time)
            sb.append("${dateFmt.format(d)},${timeFmt.format(d)},${e.sender},${e.utr},${e.type},${e.amount}\n")
        }
        return sb.toString()
    }

    fun extractUTR(text: String): String {
        val match = Regex("""(?:UPI|UTR)[:\s](\d{8,})""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.getOrNull(1) ?: ""
    }

    fun extractAmount(text: String): String {
        val match = Regex("""(?:Rs\.?\s*|INR\s*|₹\s*)([\d,]+(?:\.\d{1,2})?)""",
            setOf(RegexOption.IGNORE_CASE)).find(text)
        return match?.groupValues?.getOrNull(1)?.replace(",", "") ?: ""
    }

    fun extractPayerName(text: String): String {
        val match = Regex("""(?:from|to|by|at)\s+([A-Za-z][^.\n,@]{1,30})""", RegexOption.IGNORE_CASE).find(text)
        val raw = match?.groupValues?.getOrNull(1)?.trim() ?: return ""
        return raw.split(" ").take(3).joinToString(" ") { w ->
            w.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private fun deriveType(text: String): String {
        val lower = text.lowercase()
        val ci = lower.indexOf("credit")
        val di = lower.indexOf("debit")
        return when {
            ci >= 0 && di >= 0 -> if (ci < di) "credited" else "debited"
            ci >= 0 -> "credited"
            di >= 0 -> "debited"
            else    -> ""
        }
    }
}
