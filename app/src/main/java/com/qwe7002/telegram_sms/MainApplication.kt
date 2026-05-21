package com.qwe7002.telegram_sms

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MessageLog.migrate(this)
        scheduleDailyDump()
    }

    private fun scheduleDailyDump() {
        val req = PeriodicWorkRequestBuilder<DumpWorker>(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "nightly_dump", ExistingPeriodicWorkPolicy.KEEP, req
        )
    }
}
