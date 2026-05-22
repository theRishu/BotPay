package com.qwe7002.telegram_sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import com.qwe7002.telegram_sms.config.Config
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SMSReceiver : BroadcastReceiver() {

    companion object {
        private val client = OkHttpClient()
        const val PREFS = "botpay"
        const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
    }

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_FORWARDING_ENABLED, false)) return

        val extras = intent.extras ?: return
        val pdus = (extras["pdus"] as? Array<*>) ?: return
        val body = StringBuilder()
        var sender = ""

        for (pdu in pdus) {
            val msg = SmsMessage.createFromPdu(pdu as ByteArray, extras.getString("format"))
            body.append(msg.messageBody)
            if (sender.isEmpty()) sender = msg.originatingAddress ?: ""
        }

        val text = body.toString()
        if (text.isEmpty()) return

        val lower  = text.lowercase()
        val isCredited = lower.contains("credited")
        val isDebited  = lower.contains("debited")
        val utr        = MessageLog.extractUTR(text)
        val isDup      = MessageLog.isUTRDuplicate(context, utr)
        val shouldForward = !isDup &&
            Config.senderMatches(context, sender) &&
            Config.bodyMatches(context, text)

        val type = when {
            isCredited -> "credited"
            isDebited  -> "debited"
            else       -> ""
        }

        MessageLog.add(context, MessageLog.Entry(
            time = System.currentTimeMillis(), sender = sender, text = text,
            forwarded = shouldForward, type = type,
            amount = MessageLog.extractAmount(text), utr = utr
        ))

        if (!shouldForward) return

        val amount = MessageLog.extractAmount(text)
        val targetChatId = if (isCredited) Config.chatId(context) else Config.debitChatId(context)
        val pendingResult = goAsync()
        Thread {
            var ok = false
            try {
                val json = JSONObject().apply {
                    put("chat_id", targetChatId)
                    put("text", text)
                }
                val reqBody = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("https://api.telegram.org/bot${Config.botToken(context)}/sendMessage")
                    .post(reqBody).build()
                val resp = client.newCall(req).execute()
                ok = resp.isSuccessful
                if (ok) {
                    prefs.edit()
                        .putLong("last_forward_time", System.currentTimeMillis())
                        .putString("last_sender", sender).apply()
                }
                resp.close()
            } catch (_: Exception) {
            } finally {
                if (!ok) Notifier.alertFailedForward(context, sender, amount)
                pendingResult.finish()
            }
        }.start()
    }
}
