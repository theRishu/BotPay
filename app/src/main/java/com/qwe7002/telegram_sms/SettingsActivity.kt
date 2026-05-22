package com.qwe7002.telegram_sms

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.qwe7002.telegram_sms.config.Config

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val prefs    = getSharedPreferences("botpay_settings", Context.MODE_PRIVATE)
        val botPrefs = getSharedPreferences("botpay_settings", Context.MODE_PRIVATE)

        // ── Bot config ──
        val etToken    = findViewById<TextInputEditText>(R.id.etBotToken)
        val etChatId   = findViewById<TextInputEditText>(R.id.etChatId)
        val etSenderRegex = findViewById<TextInputEditText>(R.id.etSenderRegex)
        val etBodyRegex = findViewById<TextInputEditText>(R.id.etBodyRegex)

        etToken.setText(prefs.getString("bot_token", ""))
        etChatId.setText(prefs.getString("chat_id",  ""))
        etSenderRegex.setText(prefs.getString("sender_regex", Config.DEFAULT_SENDER_REGEX))
        etBodyRegex.setText(prefs.getString("body_regex", Config.DEFAULT_BODY_REGEX))

        findViewById<MaterialButton>(R.id.btnSaveConfig).setOnClickListener {
            prefs.edit()
                .putString("bot_token", etToken.text.toString().trim())
                .putString("chat_id",   etChatId.text.toString().trim())
                .putString("sender_regex", etSenderRegex.text.toString().trim())
                .putString("body_regex", etBodyRegex.text.toString().trim())
                .apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        // ── Manual SMS insert ──
        val etSender = findViewById<TextInputEditText>(R.id.etSmsSender)
        val etText   = findViewById<TextInputEditText>(R.id.etSmsText)

        findViewById<MaterialButton>(R.id.btnInsertSms).setOnClickListener {
            val sender = etSender.text.toString().trim()
            val text   = etText.text.toString().trim()
            if (sender.isEmpty() || text.isEmpty()) {
                Toast.makeText(this, "Fill in sender and SMS text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val utr = MessageLog.extractUTR(text)
            if (MessageLog.isUTRDuplicate(this, utr)) {
                Toast.makeText(this, "UTR $utr already logged — skipping", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val lower     = text.lowercase()
            val creditIdx = lower.indexOf("credit")
            val debitIdx  = lower.indexOf("debit")
            val type = when {
                creditIdx >= 0 && debitIdx >= 0 -> if (creditIdx < debitIdx) "credited" else "debited"
                creditIdx >= 0 -> "credited"
                debitIdx  >= 0 -> "debited"
                else           -> ""
            }
            val forward    = Config.senderMatches(this, sender) && Config.bodyMatches(this, text)
            MessageLog.add(this, MessageLog.Entry(
                time      = System.currentTimeMillis(),
                sender    = sender,
                text      = text,
                forwarded = false,
                type      = type,
                amount    = MessageLog.extractAmount(text),
                utr       = utr
            ))
            etSender.setText(""); etText.setText("")
            val msg = if (forward) "Added — tap Forward in History to send" else "Added as ignored"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }

        // ── Sync / recalculate ──
        findViewById<MaterialButton>(R.id.btnSyncNow).setOnClickListener {
            MessageLog.recalculate(this)
            Toast.makeText(this, "Done — totals recalculated", Toast.LENGTH_SHORT).show()
        }

        // ── Manual dump button ──
        findViewById<MaterialButton>(R.id.btnDumpNow).setOnClickListener {
            WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<DumpWorker>().build())
            Toast.makeText(this, "Dump queued — will appear in Telegram shortly", Toast.LENGTH_LONG).show()
        }

        // ── Summary view toggles ──
        val switchDay   = findViewById<SwitchMaterial>(R.id.switchDay)
        val switchWeek  = findViewById<SwitchMaterial>(R.id.switchWeek)
        val switchMonth = findViewById<SwitchMaterial>(R.id.switchMonth)
        val switchYear  = findViewById<SwitchMaterial>(R.id.switchYear)

        switchDay.isChecked   = prefs.getBoolean("show_day",   true)
        switchWeek.isChecked  = prefs.getBoolean("show_week",  true)
        switchMonth.isChecked = prefs.getBoolean("show_month", true)
        switchYear.isChecked  = prefs.getBoolean("show_year",  true)

        fun atLeastOneOn() = switchDay.isChecked || switchWeek.isChecked ||
                             switchMonth.isChecked || switchYear.isChecked

        switchDay.setOnCheckedChangeListener { _, checked ->
            if (!checked && !atLeastOneOn()) { switchDay.isChecked = true; return@setOnCheckedChangeListener }
            botPrefs.edit().putBoolean("show_day", checked).apply()
        }
        switchWeek.setOnCheckedChangeListener { _, checked ->
            if (!checked && !atLeastOneOn()) { switchWeek.isChecked = true; return@setOnCheckedChangeListener }
            botPrefs.edit().putBoolean("show_week", checked).apply()
        }
        switchMonth.setOnCheckedChangeListener { _, checked ->
            if (!checked && !atLeastOneOn()) { switchMonth.isChecked = true; return@setOnCheckedChangeListener }
            botPrefs.edit().putBoolean("show_month", checked).apply()
        }
        switchYear.setOnCheckedChangeListener { _, checked ->
            if (!checked && !atLeastOneOn()) { switchYear.isChecked = true; return@setOnCheckedChangeListener }
            botPrefs.edit().putBoolean("show_year", checked).apply()
        }
    }
}
