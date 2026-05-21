package com.qwe7002.telegram_sms

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.qwe7002.telegram_sms.config.Config
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class HistoryFragment : Fragment() {

    private var currentItems = listOf<LogItem>()
    private val client = OkHttpClient()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rvHistory)
        rv.layoutManager = LinearLayoutManager(requireContext())

        val swipe = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private val bgPaint = Paint().apply { color = 0xFFE53935.toInt() }
            private val textPaint = Paint().apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 38f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder) = false

            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int =
                if (vh is MessageLogAdapter.MessageVH) super.getSwipeDirs(rv, vh) else 0

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos = vh.adapterPosition
                val item = currentItems.getOrNull(pos) as? LogItem.Message ?: return
                val deleted = item.entry

                MessageLog.remove(requireContext(), deleted.time)
                refresh()

                Snackbar.make(requireView(), "Transaction deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        MessageLog.add(requireContext(), deleted)
                        refresh()
                    }.show()
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                     dX: Float, dY: Float, actionState: Int, active: Boolean) {
                val v = vh.itemView
                c.drawRect(v.right + dX, v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat(), bgPaint)
                c.drawText("DELETE", v.right - 40f, v.top + v.height / 2f + 14f, textPaint)
                super.onChildDraw(c, rv, vh, dX, dY, actionState, active)
            }
        }

        ItemTouchHelper(swipe).attachToRecyclerView(rv)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun forwardToTelegram(entry: MessageLog.Entry) {
        // Capture refs on main thread before spawning background thread
        val ctx = requireContext().applicationContext
        val act = requireActivity()
        val rootView = requireView()

        val formatted = entry.text
        Thread {
            var ok = false
            try {
                val json = JSONObject().apply {
                    put("chat_id", Config.chatId(ctx))
                    put("text", formatted)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("https://api.telegram.org/bot${Config.botToken(ctx)}/sendMessage")
                    .post(body)
                    .build()
                val resp = client.newCall(req).execute()
                ok = resp.isSuccessful
                resp.close()
            } catch (_: Exception) {}

            if (ok) {
                MessageLog.markForwarded(ctx, entry.time)
            }
            act.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                if (ok) {
                    refresh()
                    Snackbar.make(rootView, "Forwarded to Telegram", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(rootView, "Failed to forward — check internet", Snackbar.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun refresh() {
        val entries = MessageLog.getAll(requireContext())
        currentItems = MessageLogAdapter.buildItems(entries)
        requireView().findViewById<RecyclerView>(R.id.rvHistory).adapter =
            MessageLogAdapter(currentItems) { entry -> forwardToTelegram(entry) }
    }
}
