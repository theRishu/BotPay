package com.qwe7002.telegram_sms

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class TodayFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_today, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<RecyclerView>(R.id.rvTodayTx).layoutManager =
            LinearLayoutManager(requireContext())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val all = MessageLog.getAll(requireContext())
        val root = requireView()

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val today = all.filter { it.time >= todayStart }
        val todayFwd = today.filter { it.forwarded }
        val todayC = todayFwd.filter { it.type == "credited" }
        val todayD = todayFwd.filter { it.type == "debited" }
        val todayNet = sum(todayC) - sum(todayD)

        root.findViewById<TextView>(R.id.tvTodayCreditCount).text = todayC.size.toString()
        root.findViewById<TextView>(R.id.tvTodayDebitCount).text  = todayD.size.toString()
        root.findViewById<TextView>(R.id.tvTodayCreditAmt).text   = fmt(sum(todayC))
        root.findViewById<TextView>(R.id.tvTodayDebitAmt).text    = fmt(sum(todayD))
        root.findViewById<TextView>(R.id.tvTodayNet).apply {
            text = (if (todayNet >= 0) "+" else "") + fmt(todayNet)
            setTextColor(if (todayNet >= 0) 0xFF00897B.toInt() else 0xFFE53935.toInt())
        }

        val allFwd = all.filter { it.forwarded }
        val allC = allFwd.filter { it.type == "credited" }
        val allD = allFwd.filter { it.type == "debited" }
        val allNet = sum(allC) - sum(allD)

        root.findViewById<TextView>(R.id.tvAllCreditAmt).text   = fmtShort(sum(allC))
        root.findViewById<TextView>(R.id.tvAllCreditCount).text = " in  "
        root.findViewById<TextView>(R.id.tvAllDebitAmt).text    = fmtShort(sum(allD))
        root.findViewById<TextView>(R.id.tvAllDebitCount).text  = " out  "
        root.findViewById<TextView>(R.id.tvAllNet).apply {
            text = (if (allNet >= 0) "+" else "") + fmtShort(allNet)
            setTextColor(if (allNet >= 0) 0xFF00897B.toInt() else 0xFFE53935.toInt())
        }

        // Show ALL today's messages (forwarded and ignored)
        val items = today.map { LogItem.Message(it) }
        root.findViewById<RecyclerView>(R.id.rvTodayTx).adapter =
            MessageLogAdapter(items)
    }

    private fun sum(list: List<MessageLog.Entry>) = list.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    private fun fmt(v: Double) = "₹${"%.2f".format(v)}"
    private fun fmtShort(v: Double): String {
        return if (v >= 1000) "₹${"%.0f".format(v / 1000)}k" else "₹${"%.0f".format(v)}"
    }
}
