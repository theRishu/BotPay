package com.qwe7002.telegram_sms

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SummaryFragment : Fragment() {

    data class Row(val label: String, val sub: String, val credit: Double, val debit: Double)

    private enum class Mode { DAY, WEEK, MONTH, YEAR }
    private var mode = Mode.DAY

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_summary, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<RecyclerView>(R.id.rvSummary).layoutManager = LinearLayoutManager(requireContext())
        setupToggle(view)
    }

    override fun onResume() {
        super.onResume()
        setupToggle(requireView())
        refresh()
    }

    private fun setupToggle(view: View) {
        val prefs = requireContext().getSharedPreferences("botpay_settings", Context.MODE_PRIVATE)
        val showDay   = prefs.getBoolean("show_day",   true)
        val showWeek  = prefs.getBoolean("show_week",  true)
        val showMonth = prefs.getBoolean("show_month", true)
        val showYear  = prefs.getBoolean("show_year",  true)

        val btnDay   = view.findViewById<MaterialButton>(R.id.btnByDay)
        val btnWeek  = view.findViewById<MaterialButton>(R.id.btnByWeek)
        val btnMonth = view.findViewById<MaterialButton>(R.id.btnByMonth)
        val btnYear  = view.findViewById<MaterialButton>(R.id.btnByYear)

        btnDay.visibility   = if (showDay)   View.VISIBLE else View.GONE
        btnWeek.visibility  = if (showWeek)  View.VISIBLE else View.GONE
        btnMonth.visibility = if (showMonth) View.VISIBLE else View.GONE
        btnYear.visibility  = if (showYear)  View.VISIBLE else View.GONE

        // If current mode is now hidden, fall to first available
        val available = listOfNotNull(
            if (showDay)   Mode.DAY   else null,
            if (showWeek)  Mode.WEEK  else null,
            if (showMonth) Mode.MONTH else null,
            if (showYear)  Mode.YEAR  else null
        )
        if (mode !in available) mode = available.first()

        val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroup)
        toggle.clearChecked()
        toggle.check(when (mode) {
            Mode.DAY   -> R.id.btnByDay
            Mode.WEEK  -> R.id.btnByWeek
            Mode.MONTH -> R.id.btnByMonth
            Mode.YEAR  -> R.id.btnByYear
        })

        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                mode = when (checkedId) {
                    R.id.btnByWeek  -> Mode.WEEK
                    R.id.btnByMonth -> Mode.MONTH
                    R.id.btnByYear  -> Mode.YEAR
                    else            -> Mode.DAY
                }
                refresh()
            }
        }
    }

    private fun refresh() {
        val entries = MessageLog.getAll(requireContext()).filter { it.forwarded }
        val root = requireView()

        val allC = entries.filter { it.type == "credited" }
        val allD = entries.filter { it.type == "debited" }
        val totalC = sum(allC); val totalD = sum(allD)
        val netAll = totalC - totalD

        root.findViewById<TextView>(R.id.tvSumCreditAmt).text   = fmt(totalC)
        root.findViewById<TextView>(R.id.tvSumCreditCount).text = "${allC.size} transactions"
        root.findViewById<TextView>(R.id.tvSumDebitAmt).text    = fmt(totalD)
        root.findViewById<TextView>(R.id.tvSumDebitCount).text  = "${allD.size} transactions"
        root.findViewById<TextView>(R.id.tvSumNet).apply {
            text = (if (netAll >= 0) "+" else "") + fmt(netAll)
            setTextColor(if (netAll >= 0) 0xFF80DEEA.toInt() else 0xFFFF5252.toInt())
        }

        val rows = when (mode) {
            Mode.DAY   -> buildDayRows(entries)
            Mode.WEEK  -> buildWeekRows(entries)
            Mode.MONTH -> buildMonthRows(entries)
            Mode.YEAR  -> buildYearRows(entries)
        }
        val label = when (mode) { Mode.DAY -> "days"; Mode.WEEK -> "weeks"; Mode.MONTH -> "months"; else -> "years" }
        root.findViewById<TextView>(R.id.tvRowCount).text = "${rows.size} $label"
        root.findViewById<RecyclerView>(R.id.rvSummary).adapter = RowAdapter(rows)
    }

    private fun buildDayRows(entries: List<MessageLog.Entry>): List<Row> {
        val keyFmt  = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val dispFmt = SimpleDateFormat("d MMM, EEE", Locale.getDefault())
        val grouped = LinkedHashMap<String, MutableList<MessageLog.Entry>>()
        for (e in entries) grouped.getOrPut(keyFmt.format(Date(e.time))) { mutableListOf() }.add(e)
        return grouped.entries.sortedByDescending { it.key }.map { (_, list) ->
            Row(dispFmt.format(Date(list[0].time)), "${list.size} tx",
                sum(list.filter { it.type == "credited" }), sum(list.filter { it.type == "debited" }))
        }
    }

    private fun buildWeekRows(entries: List<MessageLog.Entry>): List<Row> {
        val rangeFmt = SimpleDateFormat("d MMM", Locale.getDefault())
        val grouped  = LinkedHashMap<Long, MutableList<MessageLog.Entry>>()
        for (e in entries) grouped.getOrPut(mondayOf(e.time)) { mutableListOf() }.add(e)
        return grouped.entries.sortedByDescending { it.key }.map { (monday, list) ->
            val sunday = monday + 6 * 86_400_000L
            Row("${rangeFmt.format(Date(monday))} – ${rangeFmt.format(Date(sunday))}",
                "${list.size} transactions",
                sum(list.filter { it.type == "credited" }), sum(list.filter { it.type == "debited" }))
        }
    }

    private fun buildMonthRows(entries: List<MessageLog.Entry>): List<Row> {
        val keyFmt  = SimpleDateFormat("yyyyMM", Locale.getDefault())
        val dispFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val grouped = LinkedHashMap<String, MutableList<MessageLog.Entry>>()
        for (e in entries) grouped.getOrPut(keyFmt.format(Date(e.time))) { mutableListOf() }.add(e)
        return grouped.entries.sortedByDescending { it.key }.map { (_, list) ->
            Row(dispFmt.format(Date(list[0].time)), "${list.size} transactions",
                sum(list.filter { it.type == "credited" }), sum(list.filter { it.type == "debited" }))
        }
    }

    private fun buildYearRows(entries: List<MessageLog.Entry>): List<Row> {
        val keyFmt  = SimpleDateFormat("yyyy", Locale.getDefault())
        val grouped = LinkedHashMap<String, MutableList<MessageLog.Entry>>()
        for (e in entries) grouped.getOrPut(keyFmt.format(Date(e.time))) { mutableListOf() }.add(e)
        return grouped.entries.sortedByDescending { it.key }.map { (year, list) ->
            Row(year, "${list.size} transactions",
                sum(list.filter { it.type == "credited" }), sum(list.filter { it.type == "debited" }))
        }
    }

    private fun mondayOf(time: Long): Long {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = time
            val dow = get(java.util.Calendar.DAY_OF_WEEK)
            val back = if (dow == java.util.Calendar.SUNDAY) 6 else dow - java.util.Calendar.MONDAY
            add(java.util.Calendar.DAY_OF_YEAR, -back)
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private inner class RowAdapter(private val rows: List<Row>) :
        RecyclerView.Adapter<RowAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvDate:   TextView = v.findViewById(R.id.tvSumDate)
            val tvCount:  TextView = v.findViewById(R.id.tvSumTxCount)
            val tvCredit: TextView = v.findViewById(R.id.tvSumRowCredit)
            val tvDebit:  TextView = v.findViewById(R.id.tvSumRowDebit)
            val tvNet:    TextView = v.findViewById(R.id.tvSumRowNet)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_summary_row, parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val r = rows[pos]
            val net = r.credit - r.debit
            h.tvDate.text   = r.label
            h.tvCount.text  = r.sub
            h.tvCredit.text = if (r.credit > 0) fmt(r.credit) else "—"
            h.tvDebit.text  = if (r.debit > 0)  fmt(r.debit)  else "—"
            h.tvNet.apply {
                text = (if (net >= 0) "+" else "") + fmt(net)
                setTextColor(if (net >= 0) 0xFF00897B.toInt() else 0xFFE53935.toInt())
            }
        }
    }

    private fun sum(list: List<MessageLog.Entry>) = list.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    private fun fmt(v: Double) = "₹${"%.2f".format(v)}"
}
