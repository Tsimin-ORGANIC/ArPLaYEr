package com.tsimin.arplayer

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest

data class FavFolder(val id: Long, val title: String, val count: Int)

data class VideoItem(
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val title: String,
    val duration: Int,
    val pubtime: Long,
    val author: String = ""
)

data class VideoPage(val items: List<VideoItem>, val hasMore: Boolean)

data class UserItem(val mid: Long, val uname: String, val fans: Int, val fansText: String, val sign: String)

data class UserPage(val items: List<UserItem>, val hasMore: Boolean)

data class VideoInfo(
    val aid: Long,
    val cid: Long,
    val mid: Long,
    val uname: String,
    val title: String,
    val face: String = "",
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val coinCount: Long = 0,
    val favCount: Long = 0
)

data class SpaceCard(
    val mid: Long,
    val uname: String,
    val face: String,
    val sign: String,
    val fans: Long,
    val videos: Long,
    val following: Boolean
)

data class FavPage(val items: List<VideoItem>, val hasMore: Boolean)

data class Nav(val isLogin: Boolean, val mid: Long, val uname: String, val code: Int)

data class QrPoll(
    val code: Int,
    val state: Int,
    val url: String,
    val cookies: Map<String, String>
)

data class Up(val mid: Long, val uname: String)

data class UpPage(val items: List<Up>, val hasMore: Boolean)

object Api {

    private const val APP_KEY = "1d8b6e7d45233436"
    private const val APP_SALT = "560c52ccd288fed045859ed18bffd973"

    private val mixinTable = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    )

    @Volatile private var cachedMixinKey: String? = null
    @Volatile private var mixinKeyExpire = 0L

    fun cookie(p: Prefs): String {
        val sb = StringBuilder()
        fun add(k: String, v: String) {
            if (v.isNotEmpty()) sb.append(k).append('=').append(v).append("; ")
        }
        add("SESSDATA", p.sessdata)
        add("bili_jct", p.biliJct)
        add("DedeUserID", p.dedeUserId)
        add("DedeUserID__ckMd5", p.dedeUserIdCkMd5)
        add("buvid3", p.buvid3)
        add("buvid4", p.buvid4)
        return sb.toString()
    }

    private fun ok(json: JSONObject): JSONObject {
        val code = json.optInt("code", -1)
        if (code != 0) {
            throw RuntimeException("code=$code ${json.optString("message")}")
        }
        return json
    }

    fun qrGenerate(): Pair<String, String> {
        val data = ok(
            JSONObject(Http.get("https://passport.bilibili.com/x/passport-login/web/qrcode/generate"))
        ).getJSONObject("data")
        return data.getString("url") to data.getString("qrcode_key")
    }

    fun qrPoll(key: String): QrPoll {
        val result = Http.getResult(
            "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$key"
        )
        val json = JSONObject(result.body)
        val data = json.optJSONObject("data")
        val url = data?.optString("url", "") ?: ""
        val cookies = parseRawQuery(url) + parseSetCookies(result.setCookies)
        return QrPoll(
            json.optInt("code", -1),
            data?.optInt("code", -1) ?: -1,
            url,
            cookies
        )
    }

    private fun parseRawQuery(url: String): Map<String, String> {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (part in query.split('&')) {
            val k = part.substringBefore('=', "")
            val v = part.substringAfter('=', "")
            if (k.isNotEmpty() && v.isNotEmpty()) {
                out[k] = v
            }
        }
        return out
    }

    private fun parseSetCookies(lines: List<String>): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (line in lines) {
            val pair = line.substringBefore(';')
            val k = pair.substringBefore('=', "").trim()
            val v = pair.substringAfter('=', "").trim()
            if (k.isNotEmpty() && v.isNotEmpty()) {
                out[k] = v
            }
        }
        return out
    }

    fun buvid(): Pair<String, String> {
        val data = ok(
            JSONObject(Http.get("https://api.bilibili.com/x/frontend/finger/spi"))
        ).getJSONObject("data")
        return data.getString("b_3") to data.getString("b_4")
    }

    fun nav(p: Prefs): Nav = navByCookie(cookie(p))

    fun navByCookie(cookie: String): Nav {
        val json = JSONObject(Http.get("https://api.bilibili.com/x/web-interface/nav", cookie))
        val code = json.optInt("code", -1)
        val data = json.optJSONObject("data") ?: return Nav(false, 0, "", code)
        return Nav(
            data.optBoolean("isLogin"),
            data.optLong("mid"),
            data.optString("uname"),
            code
        )
    }

    fun favFolders(p: Prefs): List<FavFolder> {
        val json = ok(
            JSONObject(
                Http.get(
                    "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=${p.mid}",
                    cookie(p)
                )
            )
        )
        val arr = json.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
        val out = ArrayList<FavFolder>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(FavFolder(o.optLong("id"), o.optString("title"), o.optInt("media_count")))
        }
        return out
    }

    fun favItems(p: Prefs, mediaId: Long, pn: Int): FavPage {
        val url = "https://api.bilibili.com/x/v3/fav/resource/list" +
            "?media_id=$mediaId&pn=$pn&ps=20&order=mtime&type=0&tid=0&platform=web"
        val json = ok(JSONObject(Http.get(url, cookie(p))))
        val data = json.optJSONObject("data") ?: return FavPage(emptyList(), false)
        val arr = data.optJSONArray("medias") ?: JSONArray()
        val out = ArrayList<VideoItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                VideoItem(
                    bvid = o.optString("bvid"),
                    aid = o.optLong("id"),
                    cid = o.optJSONObject("ugc")?.optLong("first_cid", 0) ?: 0,
                    title = o.optString("title"),
                    duration = o.optInt("duration"),
                    pubtime = o.optLong("pubtime")
                )
            )
        }
        return FavPage(out, data.optBoolean("has_more"))
    }

    fun toview(p: Prefs): List<VideoItem> {
        val json = ok(JSONObject(Http.get("https://api.bilibili.com/x/v2/history/toview", cookie(p))))
        val arr = json.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
        val out = ArrayList<VideoItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                VideoItem(
                    bvid = o.optString("bvid"),
                    aid = o.optLong("aid"),
                    cid = o.optLong("cid"),
                    title = o.optString("title"),
                    duration = o.optInt("duration"),
                    pubtime = o.optLong("pubtime")
                )
            )
        }
        return out
    }

    fun resolveCid(p: Prefs, bvid: String): Long = videoInfo(p, bvid).cid

    fun videoInfo(p: Prefs, bvid: String): VideoInfo {
        val json = ok(
            JSONObject(
                Http.get("https://api.bilibili.com/x/web-interface/view?bvid=$bvid", cookie(p))
            )
        )
        val data = json.getJSONObject("data")
        val owner = data.optJSONObject("owner")
        val stat = data.optJSONObject("stat")
        return VideoInfo(
            aid = data.optLong("aid"),
            cid = data.optLong("cid"),
            mid = owner?.optLong("mid") ?: 0L,
            uname = owner?.optString("name") ?: "",
            title = data.optString("title"),
            face = owner?.optString("face") ?: "",
            viewCount = stat?.optLong("view") ?: 0,
            likeCount = stat?.optLong("like") ?: 0,
            coinCount = stat?.optLong("coin") ?: 0,
            favCount = stat?.optLong("favorite") ?: 0
        )
    }

    fun spaceCard(p: Prefs, mid: Long): SpaceCard {
        val json = ok(
            JSONObject(
                Http.get(
                    "https://api.bilibili.com/x/web-interface/card?mid=$mid&photo=true",
                    cookie(p),
                    "https://space.bilibili.com/$mid"
                )
            )
        )
        val data = json.optJSONObject("data") ?: return SpaceCard(mid, "", "", "", 0, 0, false)
        val card = data.optJSONObject("card")
        return SpaceCard(
            mid = mid,
            uname = card?.optString("name") ?: "",
            face = card?.optString("face") ?: "",
            sign = card?.optString("sign") ?: "",
            fans = card?.optInt("fans")?.toLong() ?: 0L,
            videos = data.optInt("archive_count").toLong(),
            following = data.optBoolean("following")
        )
    }

    fun relationState(p: Prefs, mid: Long): Boolean {
        val json = JSONObject(Http.get("https://api.bilibili.com/x/relation?fid=$mid", cookie(p)))
        if (json.optInt("code", -1) != 0) return false
        val attr = json.optJSONObject("data")?.optInt("attribute", 0) ?: 0
        return attr == 2 || attr == 6
    }

    fun setFollow(p: Prefs, mid: Long, follow: Boolean): String {
        val body = "fid=$mid&act=${if (follow) 1 else 2}&re_src=14&csrf=${p.biliJct}"
        val json = JSONObject(
            Http.post(
                "https://api.bilibili.com/x/relation/modify",
                cookie(p),
                body,
                "https://space.bilibili.com/$mid"
            )
        )
        checkCode(json)
        return if (follow) "关注成功" else "已取消关注"
    }

    fun hasLike(p: Prefs, bvid: String): Boolean {
        val json = JSONObject(
            Http.get("https://api.bilibili.com/x/web-interface/archive/has/like?bvid=$bvid", cookie(p))
        )
        if (json.optInt("code", -1) != 0) return false
        return json.optInt("data", 0) == 1
    }

    fun setLike(p: Prefs, bvid: String, like: Boolean): String {
        val body = "bvid=$bvid&like=${if (like) 1 else 2}&csrf=${p.biliJct}"
        val json = JSONObject(
            Http.post(
                "https://api.bilibili.com/x/web-interface/archive/like",
                cookie(p),
                body,
                "https://www.bilibili.com/video/$bvid"
            )
        )
        checkCode(json)
        return if (like) "已点赞" else "已取消点赞"
    }

    fun coins(p: Prefs, bvid: String): Int {
        val json = JSONObject(
            Http.get("https://api.bilibili.com/x/web-interface/archive/coins?bvid=$bvid", cookie(p))
        )
        if (json.optInt("code", -1) != 0) return 0
        return json.optJSONObject("data")?.optInt("multiply", 0) ?: 0
    }

    fun addCoin(p: Prefs, bvid: String, count: Int): String {
        val body = "bvid=$bvid&multiply=$count&csrf=${p.biliJct}"
        val json = JSONObject(
            Http.post(
                "https://api.bilibili.com/x/web-interface/coin/add",
                cookie(p),
                body,
                "https://www.bilibili.com/video/$bvid"
            )
        )
        checkCode(json)
        return "已投 $count 币"
    }

    fun addFav(p: Prefs, aid: Long, folderId: Long): String {
        val body = "rid=$aid&type=2&add_media_ids=$folderId&csrf=${p.biliJct}"
        val json = JSONObject(
            Http.post(
                "https://api.bilibili.com/x/v3/fav/resource/deal",
                cookie(p),
                body,
                "https://www.bilibili.com/"
            )
        )
        checkCode(json)
        return "已加入收藏夹"
    }

    private fun checkCode(json: JSONObject) {
        val code = json.optInt("code", -1)
        if (code != 0) {
            throw RuntimeException("code=$code ${json.optString("message")}")
        }
    }

    fun followings(p: Prefs, pn: Int): UpPage {
        val url = "https://api.bilibili.com/x/relation/followings" +
            "?vmid=${p.mid}&pn=$pn&ps=50&order=desc&order_type=attention"
        val json = ok(JSONObject(Http.get(url, cookie(p))))
        val data = json.optJSONObject("data") ?: return UpPage(emptyList(), false)
        val arr = data.optJSONArray("list") ?: JSONArray()
        val out = ArrayList<Up>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Up(o.optLong("mid"), o.optString("uname")))
        }
        val total = data.optInt("total", 0)
        return UpPage(out, pn * 50 < total)
    }

    fun upVideos(p: Prefs, mid: Long, pn: Int): VideoPage {
        val query = appQuery(
            mapOf(
                "build" to "7200000",
                "mobi_app" to "android",
                "order" to "pubdate",
                "platform" to "android",
                "ps" to "20",
                "pn" to pn.toString(),
                "vmid" to mid.toString(),
                "fnval" to "0"
            )
        )
        val json = ok(
            JSONObject(
                Http.get("https://app.bilibili.com/x/v2/space/archive?$query", cookie(p))
            )
        )
        val data = json.optJSONObject("data") ?: return VideoPage(emptyList(), false)
        val arr = data.optJSONArray("item") ?: JSONArray()
        val total = data.optInt("count", 0)
        val out = ArrayList<VideoItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val bvid = o.optString("bvid")
            if (bvid.isEmpty()) continue
            out.add(
                VideoItem(
                    bvid = bvid,
                    aid = o.optLong("param"),
                    cid = o.optLong("first_cid"),
                    title = o.optString("title"),
                    duration = o.optInt("duration"),
                    pubtime = o.optLong("ctime"),
                    author = o.optString("author")
                )
            )
        }
        val more = if (total > 0) pn * 20 < total else arr.length() >= 20
        return VideoPage(out, more && out.isNotEmpty())
    }

    fun searchVideos(p: Prefs, keyword: String, pn: Int, order: String = "totalrank"): VideoPage {
        val json = searchJson(p, "video", keyword, pn, order)
        val data = json.optJSONObject("data") ?: return VideoPage(emptyList(), false)
        val arr = data.optJSONArray("result") ?: return VideoPage(emptyList(), false)
        val out = ArrayList<VideoItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val bvid = o.optString("bvid")
            if (bvid.isEmpty()) continue
            out.add(
                VideoItem(
                    bvid = bvid,
                    aid = o.optLong("aid"),
                    cid = 0L,
                    title = stripHtml(o.optString("title")),
                    duration = parseLength(o.optString("duration")),
                    pubtime = o.optLong("pubdate"),
                    author = stripHtml(o.optString("author"))
                )
            )
        }
        val pages = data.optInt("numPages", 1)
        return VideoPage(out, pn < pages && out.isNotEmpty())
    }

    fun searchUsers(p: Prefs, keyword: String, pn: Int): UserPage {
        val json = searchJson(p, "bili_user", keyword, pn, "totalrank")
        val data = json.optJSONObject("data") ?: return UserPage(emptyList(), false)
        val arr = data.optJSONArray("result") ?: return UserPage(emptyList(), false)
        val out = ArrayList<UserItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val mid = o.optLong("mid")
            if (mid <= 0) continue
            out.add(
                UserItem(
                    mid = mid,
                    uname = stripHtml(o.optString("uname")),
                    fans = o.optInt("fans"),
                    fansText = o.optString("fans"),
                    sign = o.optString("usign")
                )
            )
        }
        val pages = data.optInt("numPages", 1)
        return UserPage(out, pn < pages && out.isNotEmpty())
    }

    private fun searchJson(p: Prefs, type: String, keyword: String, pn: Int, order: String): JSONObject {
        val referer = if (type == "video") {
            "https://search.bilibili.com/all?keyword=${enc(keyword)}"
        } else {
            "https://search.bilibili.com/upuser?keyword=${enc(keyword)}"
        }
        return try {
            val query = wbiQuery(
                mapOf(
                    "search_type" to type,
                    "keyword" to keyword,
                    "page" to pn.toString(),
                    "page_size" to "30",
                    "order" to order,
                    "platform" to "pc"
                ),
                p
            )
            ok(
                JSONObject(
                    Http.get(
                        "https://api.bilibili.com/x/web-interface/wbi/search/type?$query",
                        cookie(p),
                        referer
                    )
                )
            )
        } catch (e: Exception) {
            val query = "search_type=$type&keyword=${enc(keyword)}&page=$pn&page_size=30&order=$order"
            ok(
                JSONObject(
                    Http.get(
                        "https://api.bilibili.com/x/web-interface/search/type?$query",
                        cookie(p),
                        referer
                    )
                )
            )
        }
    }

    private fun stripHtml(text: String): String = text.replace(Regex("<[^>]*>"), "")

    fun videoTags(p: Prefs, bvid: String): List<String> {
        val json = try {
            JSONObject(
                Http.get(
                    "https://api.bilibili.com/x/web-interface/view/detail/tag?bvid=$bvid",
                    cookie(p)
                )
            )
        } catch (_: Exception) {
            return emptyList()
        }
        if (json.optInt("code", -1) != 0) return emptyList()
        val arr = json.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val name = arr.optJSONObject(i)?.optString("tag_name", "") ?: ""
            if (name.isNotEmpty()) out.add(name)
        }
        return out
    }

    private fun parseLength(text: String): Int {
        if (text.isEmpty()) return 0
        var total = 0
        for (part in text.split(':')) {
            total = total * 60 + (part.trim().toIntOrNull() ?: 0)
        }
        return total
    }

    private fun appQuery(params: Map<String, String>): String {
        val all = params.toMutableMap()
        all["appkey"] = APP_KEY
        all["ts"] = (System.currentTimeMillis() / 1000).toString()
        val query = all.toSortedMap().entries.joinToString("&") { (k, v) ->
            "${enc(k)}=${enc(v)}"
        }
        return "$query&sign=${md5(query + APP_SALT)}"
    }

    private fun mixinKey(p: Prefs): String {
        val now = System.currentTimeMillis()
        val cached = cachedMixinKey
        if (cached != null && now < mixinKeyExpire) return cached
        val json = JSONObject(Http.get("https://api.bilibili.com/x/web-interface/nav", cookie(p)))
        val wbi = json.getJSONObject("data").getJSONObject("wbi_img")
        val imgKey = wbi.getString("img_url").substringAfterLast('/').substringBefore('.')
        val subKey = wbi.getString("sub_url").substringAfterLast('/').substringBefore('.')
        val orig = imgKey + subKey
        val key = buildString {
            for (i in mixinTable) {
                if (i < orig.length) append(orig[i])
            }
        }.take(32)
        cachedMixinKey = key
        mixinKeyExpire = now + 3_600_000L
        return key
    }

    private fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format("%02x", b.toInt() and 0xff))
        }
        return sb.toString()
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")

    private fun wbiQuery(params: Map<String, String>, p: Prefs): String {
        val all = params.toMutableMap()
        all["wts"] = (System.currentTimeMillis() / 1000).toString()
        val query = all.toSortedMap().entries.joinToString("&") { (k, v) ->
            "${enc(k)}=${enc(v)}"
        }
        val rid = md5(query + mixinKey(p))
        return "$query&w_rid=$rid"
    }
}
