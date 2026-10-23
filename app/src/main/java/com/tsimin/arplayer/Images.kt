package com.tsimin.arplayer

import android.widget.ImageView
import android.graphics.BitmapFactory
import kotlin.concurrent.thread

object Images {

    fun load(view: ImageView, url: String, size: Int) {
        if (url.isEmpty()) return
        val target = if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
        thread {
            try {
                val bytes = Http.getBytes(target)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@thread
                val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, size, size, true)
                view.post { view.setImageBitmap(scaled) }
            } catch (_: Exception) {
            }
        }
    }
}
