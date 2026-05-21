package com.qwe7002.telegram_sms

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class OverviewFragment : Fragment() {

    private val dayFmt  = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("d MMM", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_overview, container, false)

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val entries = MessageLog.getAll(requireContext()).filter { it.forwarded }
        val root = requireView()

        // --- TODAY ---
        val todayStart = dayStart(Calendar.getInstance())
        val todayList  = entries.filter { it.time >= todayStart }
        val todayC     = todayList.filter { it.type == "credited" }
        val todayD     = todayList.filter { it.type == "debited" }
        val todayNet   = sum(todayC) - sum(todayD)

        root.findViewById<TextView>(R.id.tvTodayCreditCount).text = todayC.size.toString()
        root.findViewById<TextView>(R.id.tvTodayDebitCount).text  = todayD.size.toString()
        root.findViewById<TextView>(R.id.tvTodayCreditAmt).text   = fmt(sum(todayC))
        root.findViewById<TextView>(R.id.tvTodayDebitAmt).text    = fmt(sum(todayD))

        val tvNet = root.findViewById<TextView>(R.id.tvTodayNet)
        tvNet.text = (if (todayNet >= 0) "+" else "") + fmt(todayNet)
        tvNet.setTextColor(if (todayNet >= 0) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())

        // --- WEEK GROUPS (Mon–Sun) newest first ---
        val container = root.findViewById<LinearLayout>(R.id.llWeeksContainer)
        container.removeAllViews()

        // Group all entries by their week's Monday
        val byWeek = LinkedHashMap<Long, MutableList<MessageLog.Entry>>()
        for (e in entries) {
            val monday = mondayOf(e.time)
            byWeek.getOrPut(monday) { mutableListOf() }.add(e)
        }

        // Sort weeks newest first
        val sortedWeeks = byWeek.entries.sortedByDescending { it.key }

        for ((monday, weekEntries) in sortedWeeks) {
            container.addView(buildWeekCard(monday, weekEntries))
        }
    }

    private fun buildWeekCard(monday: Long, entries: List<MessageLog.Entry>): View {
        val sunday = monday + 6 * 86_400_000L
        val inflater = LayoutInflater.from(requireContext())
        val dp = resources.displayMetrics.density

        val card = MaterialCardView(requireContext()).apply {
            radius = 16 * dp
            cardElevation = 0f
            setCardBackgroundColor(0xFFFFFFFF.toInt())
            strokeColor = 0xFFE8EAF6.toInt()
            strokeWidth = (1 * dp).toInt()
            val margin = (12 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, margin) }
        }

        val inner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (18 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }

        // Week header: "Mon, 19 May — Sun, 25 May"
        inner.addView(TextView(requireContext()).apply {
            text = "${dateFmt.format(Date(monday))} – ${dateFmt.format(Date(sunday))}"
            textSize = 11f
            letterSpacing = 0.05f
            setTextColor(0xFF9FA8DA.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * dp).toInt() }
        })

        // Column header row
        inner.addView(buildHeaderRow(dp))

        // Divider
        inner.addView(divider(dp, 0xFFF0F2FF.toInt()))

        // Group entries by day
        val byDay = LinkedHashMap<Long, MutableList<MessageLog.Entry>>()
        // Add all 7 days of the week
        for (i in 0..6) {
            val dayKey = monday + i * 86_400_000L
            byDay[dayKey] = mutableListOf()
        }
        for (e in entries) {
            val dk = dayStart(Calendar.getInstance().apply { timeInMillis = e.time })
            byDay.getOrPut(dk) { mutableListOf() }.add(e)
        }

        var weekC = 0.0; var weekD = 0.0

        for ((dayKey, dayEntries) in byDay) {
            val c = dayEntries.filter { it.type == "credited" }
            val d = dayEntries.filter { it.type == "debited" }
            val net = sum(c) - sum(d)
            weekC += sum(c); weekD += sum(d)

            val row = inflater.inflate(R.layout.item_week_day_row, inner, false)
            row.findViewById<TextView>(R.id.tvRowDay).text = dayFmt.format(Date(dayKey))
            row.findViewById<TextView>(R.id.tvRowCredit).apply {
                text = if (c.isEmpty()) "—" else "+${fmt(sum(c))}"
                alpha = if (c.isEmpty()) 0.3f else 1f
            }
            row.findViewById<TextView>(R.id.tvRowDebit).apply {
                text = if (d.isEmpty()) "—" else "-${fmt(sum(d))}"
                alpha = if (d.isEmpty()) 0.3f else 1f
            }
            row.findViewById<TextView>(R.id.tvRowNet).apply {
                if (dayEntries.isEmpty()) {
                    text = "—"; alpha = 0.3f
                    setTextColor(0xFF9E9E9E.toInt())
                } else {
                    text = (if (net >= 0) "+" else "") + fmt(net)
                    alpha = 1f
                    setTextColor(if (net >= 0) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
                }
            }
            inner.addView(row)
        }

        // Week total row
        inner.addView(divider(dp, 0xFF5C6BC0.toInt()).apply {
            alpha = 0.2f
            val lp = layoutParams as LinearLayout.LayoutParams
            lp.setMargins(0, (8 * dp).toInt(), 0, (4 * dp).toInt())
            layoutParams = lp
        })
        val weekNet = weekC - weekD
        inner.addView(buildTotalRow("WEEK TOTAL", weekC, weekD, weekNet, dp))

        card.addView(inner)
        return card
    }

    private fun buildHeaderRow(dp: Float): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val vPad = (4 * dp).toInt()
            setPadding(0, vPad, 0, vPad)
        }
        fun header(text: String, weight: Float, gravity: Int = Gravity.END) = TextView(requireContext()).apply {
            this.text = text; textSize = 10f; letterSpacing = 0.08f
            setTextColor(0xFFBDBDBD.toInt())
            this.gravity = gravity
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        }
        row.addView(header("DAY", 1.8f, Gravity.START))
        row.addView(header("CREDIT", 1.2f))
        row.addView(header("DEBIT", 1.2f))
        row.addView(header("NET", 1.2f))
        return row
    }

    private fun buildTotalRow(label: String, c: Double, d: Double, net: Double, dp: Float): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val vPad = (6 * dp).toInt()
            setPadding(0, vPad, 0, vPad)
        }
        fun cell(text: String, weight: Float, color: Int, gravity: Int = Gravity.END) =
            TextView(requireContext()).apply {
                this.text = text; textSize = 12f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(color); this.gravity = gravity
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            }
        row.addView(cell(label, 1.8f, 0xFF5C6BC0.toInt(), Gravity.START))
        row.addView(cell("+${fmt(c)}", 1.2f, 0xFF2E7D32.toInt()))
        row.addView(cell("-${fmt(d)}", 1.2f, 0xFFC62828.toInt()))
        row.addView(cell((if (net >= 0) "+" else "") + fmt(net), 1.2f,
            if (net >= 0) 0xFF2E7D32.toInt() else 0xFFC62828.toInt()))
        return row
    }

    private fun divider(dp: Float, color: Int = 0xFFF0F2FF.toInt()) = View(requireContext()).apply {
        setBackgroundColor(color)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
    }

    private fun mondayOf(time: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = time
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (get(Calendar.DAY_OF_WEEK) > Calendar.MONDAY &&
                Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                add(Calendar.WEEK_OF_YEAR, -1)
            }
        }
        // If the original day was before Monday (i.e., Sunday), go back a week
        val orig = Calendar.getInstance().apply { timeInMillis = time }
        if (orig.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            cal.add(Calendar.WEEK_OF_YEAR, -1)
        }
        return cal.timeInMillis
    }

    private fun dayStart(cal: Calendar): Long = Calendar.getInstance().apply {
        timeInMillis = cal.timeInMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun sum(list: List<MessageLog.Entry>) =
        list.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

    private fun fmt(amount: Double) = "₹${"%.2f".format(amount)}"
}
