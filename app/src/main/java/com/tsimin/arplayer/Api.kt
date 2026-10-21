package com.tsimin.arplayer

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

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

data class Nav(val isLogin: Boolean, val mid: Long, val uname: String)

data class QrPoll(val code: Int, val state: Int, val url: String)

object Api {

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
        val json = JSONObject(
            Http.get("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$key")
        )
        val data = json.optJSONObject("data")
        return QrPoll(
            json.optInt("code", -1),
            data?.optInt("code", -1) ?: -1,
            data?.optString("url", "") ?: ""
        )
    }

    fun parseCookieUrl(url: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return out
        for (part in query.split('&')) {
            val k = part.substringBefore('=', "")
            val v = part.substringAfter('=', "")
            if (k.isNotEmpty() && v.isNotEmpty()) {
                out[k] = URLDecoder.decode(v, "UTF-8")
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
        val data = JSONObject(
            Http.get("https://api.bilibili.com/x/web-interface/nav", cookie(p))
        ).optJSONObject("data") ?: return Nav(false, 0, "")
        return Nav(data.optBoolean("isLogin"), data.optLong("mid"), data.optString("uname"))
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
}
