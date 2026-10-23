package com.tsimin.arplayer

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import kotlin.concurrent.thread

class PlayerActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var webView: WebView
    private lateinit var titleView: TextView
    private lateinit var statsView: TextView
    private lateinit var upNameView: TextView
    private lateinit var avatarView: ImageView
    private lateinit var followBtn: Button
    private lateinit var likeBtn: Button
    private lateinit var coinBtn: Button
    private lateinit var readFavBtn: Button
    private lateinit var spaceBtn: Button

    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    private var bvid = ""
    private var aid = 0L
    private var cid = 0L
    private var upMid = 0L
    private var upName = ""
    private var followed = false
    private var liked = false
    private var coins = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        stopService(Intent(this, AudioService::class.java))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bvid = intent.getStringExtra("bvid") ?: ""
        aid = intent.getLongExtra("aid", 0L)
        cid = intent.getLongExtra("cid", 0L)
        if (bvid.isEmpty()) {
            finish()
            return
        }
        prefs.markWatched(bvid)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val playerBox = AspectBox(this)
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
        playerBox.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0E1015"))
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(28))
        }

        titleView = TextView(this).apply {
            text = "加载中…"
            textSize = 18f
            setTextColor(Color.WHITE)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        }

        statsView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(8), 0, dp(16))
        }

        val upRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        avatarView = ImageView(this).apply {
            setBackgroundColor(Color.parseColor("#1B2028"))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }
        upNameView = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        followBtn = Button(this).apply {
            text = "关注"
            textSize = 13f
            isEnabled = false
            setOnClickListener { toggleFollow() }
        }
        upRow.addView(avatarView)
        upRow.addView(upNameView)
        upRow.addView(followBtn)
        upRow.setOnClickListener { openSpace() }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        likeBtn = actionButton("点赞") { toggleLike() }
        coinBtn = actionButton("投币") { addCoin() }
        readFavBtn = actionButton("收藏") { addFav() }
        actionRow.addView(
            likeBtn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        actionRow.addView(
            coinBtn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        actionRow.addView(
            readFavBtn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        spaceBtn = Button(this).apply {
            text = "进入 UP 主详细页"
            textSize = 14f
            isEnabled = false
            setOnClickListener { openSpace() }
        }

        val bgBtn = Button(this).apply {
            text = "后台听（锁屏继续）"
            textSize = 14f
            setOnClickListener { startBackgroundAudio() }
        }

        content.addView(titleView)
        content.addView(statsView)
        content.addView(
            upRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(
            actionRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18) }
        )
        content.addView(
            spaceBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18) }
        )
        content.addView(
            bgBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        )
        scroll.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            playerBox,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)

        syncCookies()
        if (cid > 0) load(bvid, aid, cid)
        thread { fetchInfo() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun actionButton(label: String, click: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            isEnabled = false
            setOnClickListener { click() }
        }
    }

    private fun fetchInfo() {
        val info = try {
            Api.videoInfo(prefs, bvid)
        } catch (_: Exception) {
            null
        }
        if (info == null) {
            runOnUiThread {
                Toast.makeText(this, "获取视频信息失败", Toast.LENGTH_SHORT).show()
                if (cid <= 0) finish()
            }
            return
        }
        val followedNow = if (info.mid > 0) {
            try {
                Api.relationState(prefs, info.mid)
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
        val likedNow = try {
            Api.hasLike(prefs, bvid)
        } catch (_: Exception) {
            false
        }
        val coinsNow = try {
            Api.coins(prefs, bvid)
        } catch (_: Exception) {
            0
        }
        runOnUiThread {
            followed = followedNow
            liked = likedNow
            coins = coinsNow
            bindInfo(info)
            if (cid <= 0) {
                aid = info.aid
                load(bvid, info.aid, info.cid)
            }
        }
    }

    private fun bindInfo(info: VideoInfo) {
        titleView.text = info.title
        statsView.text = "播放 ${fmtCount(info.viewCount)}  ·  点赞 ${fmtCount(info.likeCount)}  ·  硬币 ${fmtCount(info.coinCount)}"
        upMid = info.mid
        upName = info.uname
        aid = info.aid
        upNameView.text = info.uname
        Images.load(avatarView, info.face, dp(44))
        updateButtons()
        followBtn.isEnabled = info.mid > 0
        likeBtn.isEnabled = true
        coinBtn.isEnabled = true
        readFavBtn.isEnabled = true
        spaceBtn.isEnabled = info.mid > 0
    }

    private fun updateButtons() {
        followBtn.text = if (followed) "已关注" else "关注"
        likeBtn.text = if (liked) "已赞" else "点赞"
        coinBtn.text = when {
            coins >= 2 -> "已投 2 币"
            coins == 1 -> "再投 1 币"
            else -> "投币"
        }
    }

    private fun toggleFollow() {
        if (upMid <= 0) return
        val target = !followed
        runAction(followBtn, { Api.setFollow(prefs, upMid, target) }) {
            followed = target
            updateButtons()
        }
    }

    private fun toggleLike() {
        val target = !liked
        runAction(likeBtn, { Api.setLike(prefs, bvid, target) }) {
            liked = target
            updateButtons()
        }
    }

    private fun addCoin() {
        if (coins >= 2) {
            Toast.makeText(this, "已经投满 2 枚了", Toast.LENGTH_SHORT).show()
            return
        }
        runAction(coinBtn, { Api.addCoin(prefs, bvid, 1) }) {
            coins += 1
            updateButtons()
        }
    }

    private fun addFav() {
        if (aid <= 0) return
        FavPicker.show(this, prefs, aid)
    }

    private fun runAction(btn: Button, action: () -> String, done: () -> Unit) {
        btn.isEnabled = false
        thread {
            var message = ""
            var ok = true
            try {
                message = action()
            } catch (e: Exception) {
                ok = false
                message = e.message ?: "未知错误"
            }
            runOnUiThread {
                btn.isEnabled = true
                Toast.makeText(
                    this,
                    if (ok) message else "操作失败：$message",
                    Toast.LENGTH_SHORT
                ).show()
                if (ok) done()
            }
        }
    }

    private fun openSpace() {
        if (upMid <= 0) return
        val intent = Intent(this, UpSpaceActivity::class.java)
        intent.putExtra("mid", upMid)
        intent.putExtra("uname", upName)
        startActivity(intent)
    }

    private fun startBackgroundAudio() {
        if (cid <= 0) {
            Toast.makeText(this, "视频信息还没加载好，稍后再试", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        Toast.makeText(this, "正在获取音频…", Toast.LENGTH_SHORT).show()
        thread {
            var error = "未知错误"
            val stream = try {
                Api.audioStream(prefs, bvid, cid)
            } catch (e: Exception) {
                error = e.message ?: "未知错误"
                null
            }
            runOnUiThread {
                if (stream == null) {
                    Toast.makeText(
                        this,
                        "获取音频失败：$error",
                        Toast.LENGTH_LONG
                    ).show()
                    return@runOnUiThread
                }
                webView.evaluateJavascript(
                    "(function(){document.querySelectorAll('video,audio').forEach(function(v){v.pause();});})()",
                    null
                )
                val svc = Intent(this, AudioService::class.java)
                svc.putExtra("url", stream.url)
                svc.putExtra(
                    "title",
                    if (titleView.text.contains("加载中")) bvid else titleView.text.toString()
                )
                svc.putExtra("artist", upName)
                svc.putExtra("referer", "https://www.bilibili.com/video/$bvid")
                svc.putExtra("cookie", Api.cookie(prefs))
                startService(svc)
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                Toast.makeText(
                    this,
                    "后台播放中（${stream.label}），可锁屏或切到其它应用",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

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

    private class AspectBox(context: Context) : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = View.MeasureSpec.getSize(widthMeasureSpec)
            super.onMeasure(
                widthMeasureSpec,
                View.MeasureSpec.makeMeasureSpec(width * 9 / 16, View.MeasureSpec.EXACTLY)
            )
        }
    }
}
