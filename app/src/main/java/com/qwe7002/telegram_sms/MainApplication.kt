package com.qwe7002.telegram_sms

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        migrateSenderRegex()
        MessageLog.migrate(this)
        scheduleDailyDump()
    }

    // Clear old ".*546.*" sender regex so all bank senders are accepted
    private fun migrateSenderRegex() {
        val prefs = getSharedPreferences("botpay_settings", MODE_PRIVATE)
        if (prefs.getString("sender_regex", "") == ".*546.*") {
            prefs.edit().remove("sender_regex").apply()
        }
    }

    private fun scheduleDailyDump() {
        val req = PeriodicWorkRequestBuilder<DumpWorker>(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "nightly_dump", ExistingPeriodicWorkPolicy.KEEP, req
        )
    }
}
