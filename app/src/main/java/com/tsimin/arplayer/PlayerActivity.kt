package com.tsimin.arplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import kotlin.concurrent.thread

class PlayerActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var webView: WebView
    private lateinit var loadingText: TextView
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val bvid = intent.getStringExtra("bvid") ?: ""
        val aid = intent.getLongExtra("aid", 0L)
        val cid = intent.getLongExtra("cid", 0L)
        if (bvid.isEmpty()) {
            finish()
            return
        }
        prefs.markWatched(bvid)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webChromeClient = chromeClient
            webViewClient = viewClient
        }

        loadingText = TextView(this).apply {
            text = "加载中…"
            setTextColor(Color.parseColor("#9AA0A6"))
            textSize = 14f
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.CENTER
            layoutParams = lp
        }

        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(loadingText)

        val actions = TextView(this).apply {
            text = "操作"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#73000000"))
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener { VideoActions.show(this@PlayerActivity, prefs, bvid) }
        }
        val actionsLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        actionsLp.gravity = Gravity.TOP or Gravity.END
        actionsLp.topMargin = dp(48)
        actionsLp.rightMargin = dp(16)
        root.addView(actions, actionsLp)

        setContentView(root)

        syncCookies()

        if (cid <= 0) {
            thread {
                try {
                    val resolved = Api.resolveCid(prefs, bvid)
                    runOnUiThread { load(bvid, aid, resolved) }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "获取视频信息失败：${e.message}", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        } else {
            load(bvid, aid, cid)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun syncCookies() {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        fun set(k: String, v: String) {
            if (v.isNotEmpty()) {
                cm.setCookie("https://www.bilibili.com", "$k=$v; domain=.bilibili.com; path=/")
            }
        }
        set("SESSDATA", prefs.sessdata)
        set("bili_jct", prefs.biliJct)
        set("DedeUserID", prefs.dedeUserId)
        set("DedeUserID__ckMd5", prefs.dedeUserIdCkMd5)
        set("buvid3", prefs.buvid3)
        set("buvid4", prefs.buvid4)
        cm.flush()
    }

    private fun load(bvid: String, aid: Long, cid: Long) {
        val url = buildString {
            append("https://www.bilibili.com/blackboard/html5mobileplayer.html?bvid=")
            append(bvid)
            if (aid > 0) append("&aid=").append(aid)
            if (cid > 0) append("&cid=").append(cid)
            append("&autoplay=1&danmaku=1&high_quality=1")
        }
        webView.loadUrl(url)
    }

    private val chromeClient = object : WebChromeClient() {
        @Suppress("DEPRECATION")
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) {
                callback.onCustomViewHidden()
                return
            }
            customView = view
            customCallback = callback
            (window.decorView as FrameLayout).addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            webView.visibility = View.GONE
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }

        override fun onHideCustomView() {
            val view = customView ?: return
            (window.decorView as FrameLayout).removeView(view)
            customView = null
            customCallback?.onCustomViewHidden()
            customCallback = null
            webView.visibility = View.VISIBLE
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private val viewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return true
            return !url.startsWith("https://www.bilibili.com/blackboard/html5mobileplayer.html") &&
                !url.startsWith("about:")
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            loadingText.visibility = View.GONE
            val css = """
                .bpx-player-ending,
                .bpx-player-ending-related,
                .bpx-player-ending-panel,
                .recommend-list,
                .recommend-list-v1,
                .bpx-player-adv-danmaku,
                .bpx-player-sending-area,
                .bpx-player-dm-setting { display: none !important; }
            """.trimIndent()
            val js = "(function(){var s=document.createElement('style');s.innerHTML=" +
                JSONObject.quote(css) + ";document.head.appendChild(s);})()"
            view?.evaluateJavascript(js, null)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (customView != null) {
            chromeClient.onHideCustomView()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.loadUrl("about:blank")
        webView.destroy()
        super.onDestroy()
    }
}
