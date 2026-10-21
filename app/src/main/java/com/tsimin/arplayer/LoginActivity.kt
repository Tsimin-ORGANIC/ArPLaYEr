package com.tsimin.arplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.concurrent.thread

class LoginActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var qrView: ImageView
    private lateinit var status: TextView
    private lateinit var refresh: Button

    @Volatile private var alive = true
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setBackgroundColor(Color.parseColor("#0E1015"))
        }

        val title = TextView(this).apply {
            text = "扫码登录"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        qrView = ImageView(this).apply {
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(240), dp(240)).apply {
                topMargin = dp(24)
                bottomMargin = dp(24)
            }
        }

        status = TextView(this).apply {
            text = "正在获取二维码…"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            gravity = Gravity.CENTER
        }

        refresh = Button(this).apply {
            text = "刷新二维码"
            visibility = View.GONE
            setOnClickListener { load() }
        }

        root.addView(title)
        root.addView(qrView)
        root.addView(status)
        root.addView(
            refresh,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        )
        setContentView(root)

        load()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun load() {
        refresh.visibility = View.GONE
        status.text = "正在获取二维码…"
        val mine = ++generation
        thread {
            try {
                val (url, key) = Api.qrGenerate()
                val bitmap = qrBitmap(url)
                runOnUiThread {
                    if (!alive || mine != generation) return@runOnUiThread
                    qrView.setImageBitmap(bitmap)
                    status.text = "请用 B站 App 扫码"
                }
                poll(key, mine)
            } catch (e: Exception) {
                runOnUiThread {
                    if (!alive || mine != generation) return@runOnUiThread
                    status.text = "获取失败：${e.message}"
                    refresh.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun poll(key: String, mine: Int) {
        while (alive && mine == generation) {
            Thread.sleep(2000)
            val r = try {
                Api.qrPoll(key)
            } catch (e: Exception) {
                continue
            }
            if (!alive || mine != generation) return
            when (r.state) {
                86101 -> runOnUiThread { status.text = "已扫码，等待确认…" }
                86090 -> runOnUiThread { status.text = "已扫码，请在手机上确认登录" }
                86038 -> {
                    runOnUiThread {
                        status.text = "二维码已失效"
                        refresh.visibility = View.VISIBLE
                    }
                    return
                }
                0 -> {
                    finishLogin(r.cookies)
                    return
                }
            }
        }
    }

    private fun finishLogin(cookies: Map<String, String>) {
        prefs.sessdata = cookies["SESSDATA"] ?: ""
        prefs.biliJct = cookies["bili_jct"] ?: ""
        prefs.dedeUserId = cookies["DedeUserID"] ?: ""
        prefs.dedeUserIdCkMd5 = cookies["DedeUserID__ckMd5"] ?: ""

        try {
            val (b3, b4) = Api.buvid()
            prefs.buvid3 = b3
            prefs.buvid4 = b4
        } catch (_: Exception) {
        }

        val nav = try {
            Api.nav(prefs)
        } catch (_: Exception) {
            null
        }

        runOnUiThread {
            if (nav != null && !nav.isLogin) {
                status.text = "登录校验失败 (${nav.code})，请重新扫码"
                refresh.visibility = View.VISIBLE
                return@runOnUiThread
            }
            if (nav != null) {
                prefs.mid = nav.mid
                prefs.uname = nav.uname
            }
            Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun qrBitmap(text: String): Bitmap {
        val size = 600
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    override fun onDestroy() {
        alive = false
        super.onDestroy()
    }
}
