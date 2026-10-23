package com.tsimin.arplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class WebLoginActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var alive = true
    @Volatile private var capturing = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E1015"))
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#141821"))
        }

        val title = TextView(this).apply {
            text = "网页登录"
            textSize = 16f
            setTextColor(Color.WHITE)
        }

        status = TextView(this).apply {
            text = "登录后自动完成"
            textSize = 12f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(dp(12), 0, dp(12), 0)
        }

        val done = Button(this).apply {
            text = "完成"
            setOnClickListener { capture() }
        }

        bar.addView(title)
        bar.addView(
            status,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        bar.addView(done)

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0E1015"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    capture()
                }
            }
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        root.addView(
            bar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            webView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)

        webView.loadUrl("https://passport.bilibili.com/login")
        handler.postDelayed(poll, 1500)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val poll = object : Runnable {
        override fun run() {
            if (!alive) return
            capture()
            handler.postDelayed(this, 2000)
        }
    }

    private fun capture() {
        if (!alive || capturing) return
        val raw = try {
            CookieManager.getInstance().getCookie("https://www.bilibili.com") ?: ""
        } catch (_: Exception) {
            ""
        }
        if (raw.isEmpty()) return

        val map = HashMap<String, String>()
        for (part in raw.split(';')) {
            val k = part.substringBefore('=', "").trim()
            val v = part.substringAfter('=', "").trim()
            if (k.isNotEmpty() && v.isNotEmpty()) map[k] = v
        }
        if (map["SESSDATA"].isNullOrEmpty()) return
        if (map["DedeUserID"].isNullOrEmpty()) return

        capturing = true
        status.text = "校验中…"

        thread {
            var nav: Nav? = null
            try {
                nav = Api.navByCookie(raw)
            } catch (_: Exception) {
            }
            val current = nav
            runOnUiThread {
                if (current != null && current.isLogin) {
                    finishLogin(map, current)
                } else {
                    capturing = false
                    val code = current?.code ?: -1
                    status.text = "校验失败 ($code)，可点完成重试"
                }
            }
        }
    }

    private fun finishLogin(map: Map<String, String>, nav: Nav) {
        prefs.sessdata = map["SESSDATA"] ?: ""
        prefs.biliJct = map["bili_jct"] ?: ""
        prefs.dedeUserId = map["DedeUserID"] ?: ""
        prefs.dedeUserIdCkMd5 = map["DedeUserID__ckMd5"] ?: ""
        prefs.mid = nav.mid
        prefs.uname = nav.uname
        try {
            val (b3, b4) = Api.buvid()
            prefs.buvid3 = b3
            prefs.buvid4 = b4
        } catch (_: Exception) {
        }
        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        alive = false
        handler.removeCallbacks(poll)
        webView.loadUrl("about:blank")
        webView.destroy()
        super.onDestroy()
    }
}
