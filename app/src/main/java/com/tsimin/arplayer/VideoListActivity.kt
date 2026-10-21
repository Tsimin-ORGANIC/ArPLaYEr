package com.tsimin.arplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class VideoListActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var listView: ListView
    private lateinit var more: Button
    private val items = ArrayList<VideoItem>()

    private var mode = "fav"
    private var mediaId = 0L
    private var page = 0
    private var hasMore = true
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        mode = intent.getStringExtra("mode") ?: "fav"
        mediaId = intent.getLongExtra("id", 0L)
        val title = intent.getStringExtra("title") ?: ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E1015"))
        }

        val head = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(24), dp(20), dp(12))
        }

        listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(Color.parseColor("#0E1015"))
        }

        more = Button(this).apply {
            text = "加载更多"
            visibility = View.GONE
            setOnClickListener { loadNext() }
        }

        root.addView(
            head,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            listView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        root.addView(
            more,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(root)

        listView.adapter = VideoAdapter()
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = items[position]
            prefs.markWatched(item.bvid)
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("bvid", item.bvid)
            intent.putExtra("aid", item.aid)
            intent.putExtra("cid", item.cid)
            startActivity(intent)
            (listView.adapter as VideoAdapter).notifyDataSetChanged()
        }

        loadNext()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun loadNext() {
        if (loading || !hasMore) return
        loading = true
        more.text = "加载中…"
        more.isEnabled = false
        val next = page + 1

        thread {
            try {
                if (mode == "toview") {
                    val list = Api.toview(prefs)
                    runOnUiThread {
                        items.clear()
                        items.addAll(list)
                        hasMore = false
                        finishPage()
                    }
                } else {
                    val result = Api.favItems(prefs, mediaId, next)
                    runOnUiThread {
                        page = next
                        items.addAll(result.items)
                        hasMore = result.hasMore
                        finishPage()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loading = false
                    more.isEnabled = true
                    more.text = "加载更多"
                    Toast.makeText(this, "加载失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun finishPage() {
        loading = false
        more.isEnabled = true
        more.text = "加载更多"
        more.visibility = if (hasMore) View.VISIBLE else View.GONE
        (listView.adapter as VideoAdapter).notifyDataSetChanged()
        if (items.isEmpty()) {
            Toast.makeText(this, "这里没有视频", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fmtDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }

    private fun fmtDate(epoch: Long): String {
        if (epoch <= 0) return ""
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epoch * 1000))
    }

    private inner class VideoAdapter : BaseAdapter() {
        override fun getCount() = items.size

        override fun getItem(position: Int) = items[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = items[position]
            val watched = prefs.isWatched(item.bvid)
            val box = LinearLayout(this@VideoListActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(14), dp(20), dp(14))
            }
            val title = TextView(this@VideoListActivity).apply {
                text = (if (watched) "✓ " else "") + item.title
                textSize = 15f
                setTextColor(if (watched) Color.parseColor("#6B7075") else Color.WHITE)
            }
            val meta = listOf(fmtDuration(item.duration), fmtDate(item.pubtime))
                .filter { it.isNotEmpty() }
                .joinToString("  ·  ")
            val sub = TextView(this@VideoListActivity).apply {
                text = meta
                textSize = 12f
                setTextColor(Color.parseColor("#9AA0A6"))
                setPadding(0, dp(4), 0, 0)
            }
            box.addView(title)
            box.addView(sub)
            return box
        }
    }
}
