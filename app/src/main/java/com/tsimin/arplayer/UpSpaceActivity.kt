package com.tsimin.arplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class UpSpaceActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var listView: ListView
    private lateinit var more: Button
    private lateinit var nameView: TextView
    private lateinit var metaView: TextView
    private lateinit var signView: TextView
    private lateinit var avatarView: ImageView
    private lateinit var followBtn: Button
    private lateinit var whitelistBtn: Button
    private lateinit var countView: TextView

    private var mid = 0L
    private var uname = ""
    private var isSelf = false
    private var followed = false

    private val items = ArrayList<VideoItem>()
    private var page = 0
    private var hasMore = true
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        mid = intent.getLongExtra("mid", 0L)
        uname = intent.getStringExtra("uname") ?: ""
        isSelf = intent.getBooleanExtra("self", false)
        if (mid <= 0) {
            Toast.makeText(this, "UP 信息无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E1015"))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(12))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        avatarView = ImageView(this).apply {
            setBackgroundColor(Color.parseColor("#1B2028"))
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        }
        val nameCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        nameView = TextView(this).apply {
            text = uname.ifEmpty { "加载中…" }
            textSize = 18f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        metaView = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(4), 0, 0)
        }
        nameCol.addView(nameView)
        nameCol.addView(metaView)
        topRow.addView(avatarView)
        topRow.addView(
            nameCol,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        signView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#8A9099"))
            setPadding(0, dp(10), 0, 0)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        followBtn = Button(this).apply {
            text = "关注"
            textSize = 13f
            setOnClickListener { toggleFollow() }
        }
        whitelistBtn = Button(this).apply {
            text = "加入白名单"
            textSize = 13f
            setOnClickListener { toggleWhitelist() }
        }
        actions.addView(
            followBtn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        actions.addView(
            whitelistBtn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        countView = TextView(this).apply {
            text = if (isSelf) "我的视频" else "全部视频"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(16), 0, dp(6))
        }

        header.addView(topRow)
        header.addView(signView)
        if (!isSelf) header.addView(actions)
        header.addView(countView)

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
            header,
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

        if (isSelf) {
            nameView.text = prefs.uname.ifEmpty { "我的主页" }
            countView.text = "我的视频"
        }
        updateWhitelist()

        listView.adapter = VideoAdapter()
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = items[position]
            prefs.markWatched(item.bvid)
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("bvid", item.bvid)
            intent.putExtra("aid", item.aid)
            startActivity(intent)
            (listView.adapter as VideoAdapter).notifyDataSetChanged()
        }
        listView.setOnItemLongClickListener { _, _, position, _ ->
            if (position < items.size) {
                VideoActions.show(this, prefs, items[position].bvid)
            }
            true
        }

        loadCard()
        loadNext()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun loadCard() {
        thread {
            val card = try {
                Api.spaceCard(prefs, mid)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (card == null || card.uname.isEmpty()) return@runOnUiThread
                uname = card.uname
                nameView.text = if (isSelf) "${card.uname}（我）" else card.uname
                metaView.text = "粉丝 ${fmtCount(card.fans)}  ·  视频 ${fmtCount(card.videos)}"
                signView.text = card.sign.ifEmpty { "" }
                Images.load(avatarView, card.face, dp(56))
                followed = card.following
                updateFollow()
            }
        }
    }

    private fun loadNext() {
        if (loading || !hasMore) return
        loading = true
        more.text = "加载中…"
        more.isEnabled = false
        more.visibility = View.VISIBLE
        val next = page + 1

        thread {
            try {
                val result = Api.upVideos(prefs, mid, next, 30)
                runOnUiThread {
                    page = next
                    items.addAll(result.items)
                    hasMore = result.hasMore
                    loading = false
                    more.text = "加载更多"
                    more.isEnabled = true
                    more.visibility = if (hasMore) View.VISIBLE else View.GONE
                    (listView.adapter as VideoAdapter).notifyDataSetChanged()
                    val suffix = if (hasMore) "+" else ""
                    val label = if (isSelf) "我的视频" else "全部视频"
                    countView.text = "$label · ${items.size}$suffix"
                    if (items.isEmpty() && !hasMore) {
                        Toast.makeText(
                            this,
                            if (isSelf) "你还没有投稿" else "ta 还没有投稿",
                            Toast.LENGTH_SHORT
                        ).show()
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

    private fun updateFollow() {
        followBtn.text = if (followed) "已关注" else "关注"
    }

    private fun toggleFollow() {
        if (mid <= 0) return
        val target = !followed
        followBtn.isEnabled = false
        thread {
            var ok = true
            try {
                Api.setFollow(prefs, mid, target)
            } catch (_: Exception) {
                ok = false
            }
            runOnUiThread {
                followBtn.isEnabled = true
                if (ok) {
                    followed = target
                    updateFollow()
                } else {
                    Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateWhitelist() {
        whitelistBtn.text = if (mid in prefs.selectedUps) "已在白名单" else "加入白名单"
    }

    private fun toggleWhitelist() {
        val selected = prefs.selectedUps.toMutableSet()
        val added = selected.add(mid)
        if (!added) selected.remove(mid)
        prefs.selectedUps = selected
        updateWhitelist()
        Toast.makeText(
            this,
            if (added) "已加入白名单" else "已移出白名单",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun fmtDate(epoch: Long): String {
        if (epoch <= 0) return ""
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epoch * 1000))
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

    private inner class VideoAdapter : BaseAdapter() {
        override fun getCount() = items.size

        override fun getItem(position: Int) = items[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = items[position]
            val watched = prefs.isWatched(item.bvid)
            val box = LinearLayout(this@UpSpaceActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(12), dp(20), dp(12))
            }
            val title = TextView(this@UpSpaceActivity).apply {
                text = (if (watched) "✓ " else "") + item.title
                textSize = 15f
                setTextColor(if (watched) Color.parseColor("#6B7075") else Color.WHITE)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            val meta = listOf(fmtDuration(item.duration), fmtDate(item.pubtime))
                .filter { it.isNotEmpty() }
                .joinToString("  ·  ")
            val sub = TextView(this@UpSpaceActivity).apply {
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
