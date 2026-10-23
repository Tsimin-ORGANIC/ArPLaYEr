package com.tsimin.arplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.concurrent.thread

class SearchActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var input: EditText
    private lateinit var listView: ListView
    private lateinit var more: Button
    private lateinit var head: TextView
    private lateinit var tabVideo: TextView
    private lateinit var tabUser: TextView
    private lateinit var orderRow: LinearLayout
    private val orderViews = ArrayList<TextView>()

    private val videos = ArrayList<VideoItem>()
    private val users = ArrayList<UserItem>()

    private var mode = "video"
    private var order = "totalrank"
    private var page = 0
    private var hasMore = false
    private var loading = false
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E1015"))
        }

        head = TextView(this).apply {
            text = "搜索"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(24), dp(20), dp(8))
        }

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), 0, dp(20), dp(8))
        }
        tabVideo = tab("视频", "video")
        tabUser = tab("UP", "user")
        tabs.addView(
            tabVideo,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        tabs.addView(
            tabUser,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val orders = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), 0, dp(20), dp(8))
        }
        for ((label, value) in listOf("综合" to "totalrank", "最新" to "pubdate", "播放" to "click")) {
            val chip = TextView(this).apply {
                text = label
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setOnClickListener { selectOrder(value) }
            }
            orderViews.add(chip)
            orders.addView(
                chip,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        this.orderRow = orders
        styleOrder()

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), 0, dp(20), dp(8))
        }

        input = EditText(this).apply {
            hint = "关键词"
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7075"))
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, _, _ ->
                search(true)
                true
            }
        }

        val go = Button(this).apply {
            text = "搜索"
            setOnClickListener { search(true) }
        }

        inputRow.addView(input)
        inputRow.addView(go)

        listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(Color.parseColor("#0E1015"))
        }

        more = Button(this).apply {
            text = "加载更多"
            visibility = View.GONE
            setOnClickListener { search(false) }
        }

        root.addView(
            head,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            tabs,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            orders,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            inputRow,
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

        selectTab("video")
        listView.adapter = ResultAdapter()
        listView.setOnItemClickListener { _, _, position, _ ->
            if (mode == "video") {
                if (position >= videos.size) return@setOnItemClickListener
                val item = videos[position]
                prefs.markWatched(item.bvid)
                val intent = Intent(this, PlayerActivity::class.java)
                intent.putExtra("bvid", item.bvid)
                intent.putExtra("aid", item.aid)
                intent.putExtra("cid", item.cid)
                startActivity(intent)
                (listView.adapter as ResultAdapter).notifyDataSetChanged()
            } else {
                if (position >= users.size) return@setOnItemClickListener
                toggle(users[position])
            }
        }
        listView.setOnItemLongClickListener { _, _, position, _ ->
            if (mode == "video" && position < videos.size) {
                VideoActions.show(this, prefs, videos[position].bvid)
                true
            } else {
                false
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun selectOrder(value: String) {
        if (order == value) return
        order = value
        styleOrder()
        if (query.isNotEmpty() && mode == "video") search(true)
    }

    private fun styleOrder() {
        for (i in 0 until orderViews.size) {
            val selected = when (i) {
                0 -> order == "totalrank"
                1 -> order == "pubdate"
                else -> order == "click"
            }
            orderViews[i].setTextColor(if (selected) Color.WHITE else Color.parseColor("#6B7075"))
            orderViews[i].setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun tab(label: String, value: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { selectTab(value) }
        }
    }

    private fun selectTab(value: String) {
        val changed = mode != value
        mode = value
        tabVideo.setTextColor(if (mode == "video") Color.WHITE else Color.parseColor("#6B7075"))
        tabUser.setTextColor(if (mode == "user") Color.WHITE else Color.parseColor("#6B7075"))
        tabVideo.setTypeface(null, if (mode == "video") Typeface.BOLD else Typeface.NORMAL)
        tabUser.setTypeface(null, if (mode == "user") Typeface.BOLD else Typeface.NORMAL)
        if (::orderRow.isInitialized) {
            orderRow.visibility = if (mode == "video") View.VISIBLE else View.GONE
        }
        if (changed) {
            videos.clear()
            users.clear()
            page = 0
            hasMore = false
            head.text = "搜索"
            more.visibility = View.GONE
            if (::listView.isInitialized) {
                (listView.adapter as ResultAdapter).notifyDataSetChanged()
            }
            if (query.isNotEmpty()) search(true)
        }
    }

    private fun search(reset: Boolean) {
        if (loading) return
        val text = input.text.toString().trim()
        if (reset) {
            if (text.isEmpty()) {
                Toast.makeText(this, "输入关键词", Toast.LENGTH_SHORT).show()
                return
            }
            query = text
            videos.clear()
            users.clear()
            page = 0
            hasMore = false
            (listView.adapter as ResultAdapter).notifyDataSetChanged()
        }
        if (query.isEmpty()) return
        if (!reset && !hasMore) return

        loading = true
        more.isEnabled = false
        more.text = "加载中…"
        more.visibility = View.VISIBLE
        val m = mode
        val next = page + 1

        thread {
            try {
                if (m == "video") {
                    val result = Api.searchVideos(prefs, query, next, order)
                    runOnUiThread {
                        if (mode != m) {
                            loading = false
                            return@runOnUiThread
                        }
                        page = next
                        videos.addAll(result.items)
                        hasMore = result.hasMore
                        finishSearch()
                    }
                } else {
                    val result = Api.searchUsers(prefs, query, next)
                    runOnUiThread {
                        if (mode != m) {
                            loading = false
                            return@runOnUiThread
                        }
                        page = next
                        users.addAll(result.items)
                        hasMore = result.hasMore
                        finishSearch()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loading = false
                    more.isEnabled = true
                    more.text = "加载更多"
                    Toast.makeText(this, "搜索失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun finishSearch() {
        loading = false
        more.isEnabled = true
        more.text = "加载更多"
        more.visibility = if (hasMore) View.VISIBLE else View.GONE
        val count = if (mode == "video") videos.size else users.size
        head.text = "搜索 · $count 个"
        (listView.adapter as ResultAdapter).notifyDataSetChanged()
        if (count == 0) {
            Toast.makeText(this, "没有结果", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggle(user: UserItem) {
        val selected = prefs.selectedUps.toMutableSet()
        val added = selected.add(user.mid)
        if (!added) selected.remove(user.mid)
        prefs.selectedUps = selected
        Toast.makeText(
            this,
            if (added) "已加入白名单：${user.uname}" else "已移除：${user.uname}",
            Toast.LENGTH_SHORT
        ).show()
        (listView.adapter as ResultAdapter).notifyDataSetChanged()
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

    private inner class ResultAdapter : BaseAdapter() {
        override fun getCount() = if (mode == "video") videos.size else users.size

        override fun getItem(position: Int): Any =
            if (mode == "video") videos[position] else users[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return if (mode == "video") videoRow(videos[position]) else userRow(users[position])
        }

        private fun videoRow(item: VideoItem): View {
            val watched = prefs.isWatched(item.bvid)
            val box = LinearLayout(this@SearchActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(14), dp(20), dp(14))
            }
            val title = TextView(this@SearchActivity).apply {
                text = (if (watched) "✓ " else "") + item.title
                textSize = 15f
                setTextColor(if (watched) Color.parseColor("#6B7075") else Color.WHITE)
            }
            val meta = listOf(item.author, fmtDuration(item.duration))
                .filter { it.isNotEmpty() }
                .joinToString("  ·  ")
            val sub = TextView(this@SearchActivity).apply {
                text = meta
                textSize = 12f
                setTextColor(Color.parseColor("#9AA0A6"))
                setPadding(0, dp(4), 0, 0)
            }
            box.addView(title)
            if (meta.isNotEmpty()) box.addView(sub)
            return box
        }

        private fun userRow(user: UserItem): View {
            val box = LinearLayout(this@SearchActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(10), dp(20), dp(10))
            }
            val row = CheckBox(this@SearchActivity).apply {
                text = user.uname
                textSize = 15f
                setTextColor(Color.WHITE)
                isChecked = user.mid in prefs.selectedUps
                isClickable = false
                isFocusable = false
                setPadding(0, 0, 0, 0)
            }
            val fans = if (user.fansText.isNotEmpty()) user.fansText else user.fans.toString()
            val meta = listOf("粉丝 $fans", user.sign)
                .filter { it.isNotEmpty() }
                .joinToString("  ·  ")
            val sub = TextView(this@SearchActivity).apply {
                text = meta
                textSize = 12f
                setTextColor(Color.parseColor("#9AA0A6"))
                setPadding(dp(32), 0, 0, dp(4))
            }
            box.addView(row)
            box.addView(sub)
            return box
        }
    }
}
