package com.qwe7002.telegram_sms

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class LogItem {
    data class DateHeader(val label: String) : LogItem()
    data class Message(val entry: MessageLog.Entry) : LogItem()
}

class MessageLogAdapter(
    private val items: List<LogItem>,
    private val onForward: ((MessageLog.Entry) -> Unit)? = null,
    private val compact: Boolean = false
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_MESSAGE = 1

        // Emerald green for forwarded, rose for debit label in amount
        private const val COLOR_FORWARD = 0xFF059669.toInt()
        private const val COLOR_DEBIT   = 0xFFE11D48.toInt()
        private const val COLOR_IGNORED = 0xFF9CA3AF.toInt()

        fun buildItems(entries: List<MessageLog.Entry>): List<LogItem> {
            val result = mutableListOf<LogItem>()
            var lastLabel = ""
            for (entry in entries) {
                val label = dateLabel(entry.time)
                if (label != lastLabel) {
                    result.add(LogItem.DateHeader(label))
                    lastLabel = label
                }
                result.add(LogItem.Message(entry))
            }
            return result
        }

        private fun dateLabel(time: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = time }
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            return when {
                sameDay(cal, today) -> "Today"
                sameDay(cal, yesterday) -> "Yesterday"
                else -> SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(time))
            }
        }

        private fun sameDay(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                    a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvLabel: TextView = view.findViewById(R.id.tvDateLabel)
    }

    class MessageVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tvMsgIcon)
        val tvSender: TextView = view.findViewById(R.id.tvMsgSender)
        val tvText: TextView = view.findViewById(R.id.tvMsgText)
        val tvAmount: TextView = view.findViewById(R.id.tvMsgAmount)
        val tvTime: TextView = view.findViewById(R.id.tvMsgTime)
        val btnForward: MaterialButton = view.findViewById(R.id.btnForward)
    }

    override fun getItemViewType(position: Int) =
        if (items[position] is LogItem.DateHeader) TYPE_HEADER else TYPE_MESSAGE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER)
            HeaderVH(inf.inflate(R.layout.item_date_header, parent, false))
        else
            MessageVH(inf.inflate(R.layout.item_message, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is LogItem.DateHeader -> (holder as HeaderVH).tvLabel.text = item.label
            is LogItem.Message -> bind(holder as MessageVH, item.entry)
        }
    }

    private fun bind(h: MessageVH, e: MessageLog.Entry) {
        h.tvTime.text = timeFmt.format(Date(e.time))
        if (compact) {
            val payer = MessageLog.extractPayerName(e.text)
            h.tvSender.text = if (payer.isNotEmpty()) payer else e.sender
            h.tvText.visibility = View.GONE
        } else {
            h.tvSender.text = e.sender
            h.tvText.text = e.text
            h.tvText.visibility = View.VISIBLE
        }
        h.btnForward.visibility = View.GONE

        if (e.forwarded) {
            // Derive type/amount from stored fields, fall back to re-parsing the text
            val lower = e.text.lowercase()
            val type = when {
                e.type == "credited" -> "credited"
                e.type == "debited" -> "debited"
                lower.contains("credited") -> "credited"
                lower.contains("debited") -> "debited"
                else -> ""
            }
            val amount = if (e.amount.isNotEmpty()) e.amount else MessageLog.extractAmount(e.text)

            h.tvIcon.background = circle(COLOR_FORWARD)
            when (type) {
                "credited" -> {
                    h.tvIcon.text = "↑"
                    h.tvAmount.text = if (amount.isNotEmpty()) "+₹$amount" else "Credited"
                }
                "debited" -> {
                    h.tvIcon.text = "↓"
                    h.tvAmount.text = if (amount.isNotEmpty()) "-₹$amount" else "Debited"
                }
                else -> {
                    h.tvIcon.text = "✓"
                    h.tvAmount.text = if (amount.isNotEmpty()) "₹$amount" else "Forwarded"
                }
            }
            h.tvAmount.setTextColor(COLOR_FORWARD)
        } else {
            // Not forwarded → gray, show forward button
            h.tvIcon.text = "·"
            h.tvIcon.background = circle(COLOR_IGNORED)
            h.tvAmount.text = "Ignored"
            h.tvAmount.setTextColor(COLOR_IGNORED)
            if (onForward != null) {
                h.btnForward.visibility = View.VISIBLE
                h.btnForward.setOnClickListener { onForward.invoke(e) }
            }
        }
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    override fun getItemCount() = items.size
}
