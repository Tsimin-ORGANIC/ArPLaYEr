package com.tsimin.arplayer

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class UpListActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var listView: ListView
    private lateinit var more: Button
    private lateinit var head: TextView
    private val ups = ArrayList<Up>()

    private var pn = 1
    private var hasMore = true
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E1015"))
        }

        head = TextView(this).apply {
            text = "关注的 UP · 已选 ${prefs.selectedUps.size}"
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

        listView.adapter = UpAdapter()
        listView.setOnItemClickListener { _, _, position, _ ->
            toggle(ups[position])
        }

        loadNext()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toggle(up: Up) {
        val selected = prefs.selectedUps.toMutableSet()
        if (!selected.add(up.mid)) {
            selected.remove(up.mid)
        }
        prefs.selectedUps = selected
        head.text = "关注的 UP · 已选 ${selected.size}"
        (listView.adapter as UpAdapter).notifyDataSetChanged()
    }

    private fun loadNext() {
        if (loading || !hasMore) return
        loading = true
        more.text = "加载中…"
        more.isEnabled = false
        val next = pn

        thread {
            try {
                val page = Api.followings(prefs, next)
                runOnUiThread {
                    pn = next + 1
                    ups.addAll(page.items)
                    hasMore = page.hasMore
                    loading = false
                    more.isEnabled = true
                    more.text = "加载更多"
                    more.visibility = if (hasMore) View.VISIBLE else View.GONE
                    (listView.adapter as UpAdapter).notifyDataSetChanged()
                    if (ups.isEmpty()) {
                        Toast.makeText(this, "关注列表为空", Toast.LENGTH_SHORT).show()
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

    private inner class UpAdapter : BaseAdapter() {
        override fun getCount() = ups.size

        override fun getItem(position: Int) = ups[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val up = ups[position]
            return CheckBox(this@UpListActivity).apply {
                text = up.uname
                textSize = 15f
                setTextColor(Color.WHITE)
                isChecked = up.mid in prefs.selectedUps
                isClickable = false
                isFocusable = false
                setPadding(dp(20), dp(10), dp(20), dp(10))
            }
        }
    }
}
