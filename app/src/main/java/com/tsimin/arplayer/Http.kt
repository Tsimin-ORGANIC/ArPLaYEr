package com.tsimin.arplayer

import java.net.HttpURLConnection
import java.net.URL

data class HttpResult(val body: String, val setCookies: List<String>)

object Http {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 15; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    fun userAgent(): String = UA

    fun get(
        url: String,
        cookie: String? = null,
        referer: String = "https://www.bilibili.com/"
    ): String = getResult(url, cookie, referer).body

    fun post(
        url: String,
        cookie: String?,
        body: String,
        referer: String = "https://www.bilibili.com/"
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Referer", referer)
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            if (!cookie.isNullOrBlank()) {
                conn.setRequestProperty("Cookie", cookie)
            }
            val bytes = body.toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    fun getBytes(
        url: String,
        referer: String = "https://www.bilibili.com/"
    ): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Referer", referer)
            val code = conn.responseCode
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code")
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    fun getResult(
        url: String,
        cookie: String? = null,
        referer: String = "https://www.bilibili.com/"
    ): HttpResult {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Referer", referer)
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
