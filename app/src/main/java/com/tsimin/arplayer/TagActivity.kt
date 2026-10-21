package com.tsimin.arplayer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class TagActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var input: EditText
    private lateinit var listView: ListView
    private val tags = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E1015"))
        }

        val head = TextView(this).apply {
            text = "标签过滤"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(24), dp(20), dp(4))
        }

        val hint = TextView(this).apply {
            text = "UP 更新里只保留命中任一标签的视频（长按删除）"
            textSize = 12f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(dp(20), 0, dp(20), dp(12))
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), 0, dp(20), dp(12))
        }

        input = EditText(this).apply {
            hint = "输入标签"
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7075"))
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val add = Button(this).apply {
            text = "添加"
            setOnClickListener { addTag() }
        }

        inputRow.addView(input)
        inputRow.addView(add)

        listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(Color.parseColor("#0E1015"))
        }

        root.addView(
            head,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            hint,
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
        setContentView(root)

        listView.adapter = TagAdapter()
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val tag = tags[position]
            AlertDialog.Builder(this)
                .setMessage("删除标签「$tag」？")
                .setPositiveButton("删除") { _, _ ->
                    val next = prefs.tags.toMutableSet()
                    next.remove(tag)
                    prefs.tags = next
                    refresh()
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        refresh()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun addTag() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        if (prefs.tags.any { it.equals(text, ignoreCase = true) }) {
            Toast.makeText(this, "已经加过了", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.tags = prefs.tags + text
        input.setText("")
        refresh()
    }

    private fun refresh() {
        tags.clear()
        tags.addAll(prefs.tags.sorted())
        (listView.adapter as TagAdapter).notifyDataSetChanged()
    }

    private inner class TagAdapter : BaseAdapter() {
        override fun getCount() = tags.size

        override fun getItem(position: Int) = tags[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return TextView(this@TagActivity).apply {
                text = tags[position]
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(dp(20), dp(14), dp(20), dp(14))
            }
        }
    }
}
