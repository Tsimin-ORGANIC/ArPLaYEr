package com.tsimin.arplayer

import java.net.HttpURLConnection
import java.net.URL

data class HttpResult(val body: String, val setCookies: List<String>)

object Http {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 15; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    fun get(url: String, cookie: String? = null): String = getResult(url, cookie).body

    fun getResult(url: String, cookie: String? = null): HttpResult {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Referer", "https://www.bilibili.com/")
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            if (!cookie.isNullOrBlank()) {
                conn.setRequestProperty("Cookie", cookie)
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code")
            }
            val setCookies = conn.headerFields.entries
                .filter { "Set-Cookie".equals(it.key, ignoreCase = true) }
                .flatMap { it.value ?: emptyList() }
            return HttpResult(text, setCookies)
        } finally {
            conn.disconnect()
        }
    }
}
