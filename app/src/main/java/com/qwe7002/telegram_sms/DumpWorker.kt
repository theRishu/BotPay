package com.qwe7002.telegram_sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.qwe7002.telegram_sms.config.Config
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class DumpWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val csv = MessageLog.exportCSV(ctx)
        if (csv.lines().size <= 1) return Result.success() // nothing to dump

        val msg = "📊 Daily Transaction Dump\n\n```\n$csv```"
        return try {
            val json = JSONObject().apply {
                put("chat_id",    Config.chatId(ctx))
                put("text",       msg)
                put("parse_mode", "Markdown")
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req  = Request.Builder()
                .url("https://api.telegram.org/bot${Config.botToken(ctx)}/sendMessage")
                .post(body).build()
            val resp = OkHttpClient().newCall(req).execute()
            resp.close()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
