package com.qwe7002.telegram_sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(SMSReceiver.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SMSReceiver.KEY_FORWARDING_ENABLED, false)) return

        val svc = Intent(context, BotPayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}
