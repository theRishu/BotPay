package com.qwe7002.telegram_sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BotPayService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(SMSReceiver.PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(SMSReceiver.KEY_FORWARDING_ENABLED, false)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel("botpay_service", "BotPay", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val notif = NotificationCompat.Builder(this, "botpay_service")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("BotPay active")
            .setContentText("Monitoring bank SMS")
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(101, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(101, notif)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
