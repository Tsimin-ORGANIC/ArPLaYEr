package com.tsimin.arplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var listView: ListView
    private lateinit var header: TextView
    private val rows = ArrayList<Row>()

    private data class Row(val kind: String, val id: Long, val title: String, val subtitle: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (!prefs.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E1015"))
        }

        header = TextView(this).apply {
            text = "ArPLaYEr"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(24), dp(20), dp(8))
            setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("退出登录？")
                    .setPositiveButton("退出") { _, _ ->
                        prefs.clear()
                        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                        finish()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
        }

        listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(Color.parseColor("#0E1015"))
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
        setContentView(root)

        listView.adapter = RowAdapter()
        listView.setOnItemClickListener { _, _, position, _ ->
            val row = rows[position]
            val intent = Intent(this, VideoListActivity::class.java)
            intent.putExtra("mode", row.kind)
            intent.putExtra("id", row.id)
            intent.putExtra("title", row.title)
            startActivity(intent)
        }

        load()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun load() {
        thread {
            try {
                val nav = Api.nav(prefs)
                if (!nav.isLogin) {
                    runOnUiThread {
                        Toast.makeText(this, "登录已失效", Toast.LENGTH_SHORT).show()
                        prefs.clear()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                    return@thread
                }
                prefs.mid = nav.mid
                prefs.uname = nav.uname

                val folders = Api.favFolders(prefs)
                val toview = Api.toview(prefs)

                runOnUiThread {
                    header.text = nav.uname.ifEmpty { "ArPLaYEr" }
                    rows.clear()
                    if (toview.isNotEmpty()) {
                        rows.add(Row("toview", 0L, "稍后再看", "${toview.size} 个视频"))
                    }
                    for (f in folders) {
                        rows.add(Row("fav", f.id, f.title, "${f.count} 个视频"))
                    }
                    (listView.adapter as RowAdapter).notifyDataSetChanged()
                    if (rows.isEmpty()) {
                        Toast.makeText(this, "没有可播放的内容", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "加载失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private inner class RowAdapter : BaseAdapter() {
        override fun getCount() = rows.size

        override fun getItem(position: Int) = rows[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = rows[position]
            val box = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16))
            }
            val title = TextView(this@MainActivity).apply {
                text = row.title
                textSize = 16f
                setTextColor(Color.WHITE)
            }
            val sub = TextView(this@MainActivity).apply {
                text = row.subtitle
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
