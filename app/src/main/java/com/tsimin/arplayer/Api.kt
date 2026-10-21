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
    val pubtime: Long
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

    fun nav(p: Prefs): Nav {
        val json = JSONObject(Http.get("https://api.bilibili.com/x/web-interface/nav", cookie(p)))
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

    fun resolveCid(p: Prefs, bvid: String): Long {
        val json = ok(
            JSONObject(
                Http.get("https://api.bilibili.com/x/web-interface/view?bvid=$bvid", cookie(p))
            )
        )
        return json.getJSONObject("data").optLong("cid", 0)
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

    fun upVideos(p: Prefs, mid: Long, ps: Int): List<VideoItem> {
        val query = wbiQuery(
            mapOf(
                "mid" to mid.toString(),
                "ps" to ps.toString(),
                "pn" to "1",
                "order" to "pubdate",
                "tid" to "0",
                "keyword" to "",
                "platform" to "web",
                "web_location" to "1550101"
            ),
            p
        )
        val json = ok(
            JSONObject(
                Http.get(
                    "https://api.bilibili.com/x/space/wbi/arc/search?$query",
                    cookie(p),
                    "https://space.bilibili.com/$mid/video"
                )
            )
        )
        val arr = json.optJSONObject("data")
            ?.optJSONObject("list")
            ?.optJSONArray("vlist")
            ?: return emptyList()
        val out = ArrayList<VideoItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                VideoItem(
                    bvid = o.optString("bvid"),
                    aid = o.optLong("aid"),
                    cid = 0L,
                    title = o.optString("title"),
                    duration = parseLength(o.optString("length")),
                    pubtime = o.optLong("created")
                )
            )
        }
        return out
    }

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
