package com.qwe7002.telegram_sms

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WeekPageFragment : Fragment() {

    companion object {
        private const val ARG_OFFSET = "week_offset"
        fun newInstance(weekOffset: Int) = WeekPageFragment().apply {
            arguments = Bundle().apply { putInt(ARG_OFFSET, weekOffset) }
        }
    }

    private val rangeFmt = SimpleDateFormat("d MMM", Locale.getDefault())
    private val dayFmt   = SimpleDateFormat("EEE, d MMM", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_week_page, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val offset = arguments?.getInt(ARG_OFFSET) ?: 0
        val monday = mondayOfWeek(offset)
        populateWeek(view, monday)
    }

    private fun populateWeek(root: View, monday: Long) {
        val sunday = monday + 6 * 86_400_000L
        val entries = MessageLog.getAll(requireContext()).filter { it.forwarded }

        root.findViewById<TextView>(R.id.tvWeekRange).text =
            "${rangeFmt.format(Date(monday))} – ${rangeFmt.format(Date(sunday))}"

        val llRows  = root.findViewById<LinearLayout>(R.id.llDayRows)
        val llTotal = root.findViewById<LinearLayout>(R.id.llWeekTotal)
        llRows.removeAllViews()
        llTotal.removeAllViews()

        val dp = resources.displayMetrics.density
        var weekC = 0.0; var weekD = 0.0

        for (i in 0..6) {
            val dayStart = monday + i * 86_400_000L
            val dayEnd   = dayStart + 86_400_000L
            val dayList  = entries.filter { it.time >= dayStart && it.time < dayEnd }
            val c = dayList.filter { it.type == "credited" }
            val d = dayList.filter { it.type == "debited" }
            val net = sum(c) - sum(d)
            weekC += sum(c); weekD += sum(d)

            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_week_day_row, llRows, false)

            val isToday = isSameDay(dayStart, todayStart())
            row.findViewById<TextView>(R.id.tvRowDay).apply {
                text = dayFmt.format(Date(dayStart))
                if (isToday) setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (isToday) 0xFF1A237E.toInt() else 0xFF424242.toInt())
            }
            row.findViewById<TextView>(R.id.tvRowCredit).apply {
                text = if (c.isEmpty()) "—" else "+${fmt(sum(c))}"
                alpha = if (c.isEmpty()) 0.3f else 1f
            }
            row.findViewById<TextView>(R.id.tvRowDebit).apply {
                text = if (d.isEmpty()) "—" else "-${fmt(sum(d))}"
                alpha = if (d.isEmpty()) 0.3f else 1f
            }
            row.findViewById<TextView>(R.id.tvRowNet).apply {
                if (dayList.isEmpty()) {
                    text = "—"; alpha = 0.3f; setTextColor(0xFF9E9E9E.toInt())
                } else {
                    alpha = 1f
                    text = (if (net >= 0) "+" else "") + fmt(net)
                    setTextColor(if (net >= 0) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
                }
            }

            llRows.addView(row)

            // Divider between rows
            if (i < 6) llRows.addView(View(requireContext()).apply {
                setBackgroundColor(0xFFF0F2FF.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                )
            })
        }

        // Week net header card
        val weekNet = weekC - weekD
        root.findViewById<TextView>(R.id.tvWeekNet).apply {
            text = (if (weekNet >= 0) "+" else "") + fmt(weekNet)
            setTextColor(if (weekNet >= 0) 0xFF69F0AE.toInt() else 0xFFFF5252.toInt())
        }

        // Total row
        fun totalCell(text: String, weight: Float, color: Int, grav: Int = Gravity.END) =
            TextView(requireContext()).apply {
                this.text = text; textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color); gravity = grav
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            }
        llTotal.addView(totalCell("TOTAL", 1.8f, 0xFF5C6BC0.toInt(), Gravity.START))
        llTotal.addView(totalCell("+${fmt(weekC)}", 1.2f, 0xFF2E7D32.toInt()))
        llTotal.addView(totalCell("-${fmt(weekD)}", 1.2f, 0xFFC62828.toInt()))
        llTotal.addView(totalCell(
            (if (weekNet >= 0) "+" else "") + fmt(weekNet), 1.2f,
            if (weekNet >= 0) 0xFF2E7D32.toInt() else 0xFFC62828.toInt()
        ))
    }

    private fun mondayOfWeek(offset: Int): Long {
        val cal = Calendar.getInstance().apply {
            // Set to Monday of current week
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, -1)
            // Go back by offset weeks
            add(Calendar.WEEK_OF_YEAR, -offset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun todayStart() = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
                ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun sum(list: List<MessageLog.Entry>) = list.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    private fun fmt(v: Double) = "₹${"%.2f".format(v)}"
}
