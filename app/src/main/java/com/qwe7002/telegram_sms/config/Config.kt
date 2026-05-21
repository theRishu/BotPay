package com.qwe7002.telegram_sms.config

import android.content.Context

object Config {
    private const val DEFAULT_BOT_TOKEN = "0000000000:FAKE_BOT_TOKEN_REPLACE_ME"
    private const val DEFAULT_CHAT_ID   = "-1000000000000"
    private const val DEFAULT_KEYWORDS  = ""
    private const val DEFAULT_SENDER_REGEX = ".*546.*"
    private const val DEFAULT_BODY_REGEX = ".*(credited|debited).*"

    fun botToken(ctx: Context): String =
        prefs(ctx).getString("bot_token", "")!!.ifBlank { DEFAULT_BOT_TOKEN }

    fun chatId(ctx: Context): String =
        prefs(ctx).getString("chat_id", "")!!.ifBlank { DEFAULT_CHAT_ID }

    fun keywords(ctx: Context): List<String> =
        prefs(ctx).getString("keywords", DEFAULT_KEYWORDS)!!
            .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    fun senderMatches(ctx: Context, sender: String): Boolean =
        regex(ctx, "sender_regex", DEFAULT_SENDER_REGEX).containsMatchIn(sender)

    fun bodyMatches(ctx: Context, body: String): Boolean =
        regex(ctx, "body_regex", DEFAULT_BODY_REGEX).containsMatchIn(body)

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("botpay_settings", Context.MODE_PRIVATE)

    private fun regex(ctx: Context, key: String, defaultPattern: String): Regex {
        val pattern = prefs(ctx).getString(key, defaultPattern)!!.ifBlank { defaultPattern }
        return try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            Regex(defaultPattern, RegexOption.IGNORE_CASE)
        }
    }
}
