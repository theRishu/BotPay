package com.qwe7002.telegram_sms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissionsIfNeeded()
        enableForwardingIfAllowed()
        requestBatteryExemptionIfNeeded()

        val pager = findViewById<ViewPager2>(R.id.viewPager)
        pager.adapter = MainPagerAdapter(this)
        pager.isUserInputEnabled = false  // tabs are tap-only; swipe belongs to week pager

        TabLayoutMediator(findViewById(R.id.tabLayout), pager) { tab, pos ->
            tab.text = when (pos) { 0 -> "Today"; 1 -> "Week"; 2 -> "Summary"; else -> "History" }
        }.attach()

        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnGrantSms).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        enableForwardingIfAllowed()
        refreshHeader()
    }

    private fun startBotPayService() {
        val svc = Intent(this, BotPayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)
    }

    private fun enableForwardingIfAllowed() {
        if (!hasSmsPermissions()) return
        getSharedPreferences(SMSReceiver.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SMSReceiver.KEY_FORWARDING_ENABLED, true)
            .apply()
        startBotPayService()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (!hasPerm(Manifest.permission.RECEIVE_SMS)) needed.add(Manifest.permission.RECEIVE_SMS)
        if (!hasPerm(Manifest.permission.READ_SMS)) needed.add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPerm(Manifest.permission.POST_NOTIFICATIONS)
        ) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
    }

    private fun requestBatteryExemptionIfNeeded() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {}
        }
    }

    private fun refreshHeader() {
        val smsOk = hasSmsPermissions()
        val forwardingEnabled = getSharedPreferences(SMSReceiver.PREFS, Context.MODE_PRIVATE)
            .getBoolean(SMSReceiver.KEY_FORWARDING_ENABLED, false)

        val dot = findViewById<TextView>(R.id.tvStatusDot)
        val tvState = findViewById<TextView>(R.id.tvState)
        if (smsOk && forwardingEnabled) {
            dot.setTextColor(0xFF69F0AE.toInt())
            tvState.text = "Active"
            tvState.setTextColor(0xFF69F0AE.toInt())
        } else {
            dot.setTextColor(0xFFFF5252.toInt())
            tvState.text = "No Permission"
            tvState.setTextColor(0xFFFF5252.toInt())
        }

        findViewById<MaterialCardView>(R.id.cardSmsPermission).visibility =
            if (smsOk) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun hasSmsPermissions() =
        hasPerm(Manifest.permission.RECEIVE_SMS) && hasPerm(Manifest.permission.READ_SMS)

    private fun hasPerm(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        enableForwardingIfAllowed()
        refreshHeader()
    }
}
