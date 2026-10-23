package com.tsimin.arplayer

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import kotlin.concurrent.thread

object FavPicker {

    fun show(activity: Activity, prefs: Prefs, aid: Long, onDone: (() -> Unit)? = null) {
        if (aid <= 0) {
            Toast.makeText(activity, "视频信息无效", Toast.LENGTH_SHORT).show()
            return
        }
        thread {
            val folders = try {
                Api.favFolders(prefs)
            } catch (_: Exception) {
                null
            }
            activity.runOnUiThread {
                if (folders == null) {
                    Toast.makeText(activity, "获取收藏夹失败", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (folders.isEmpty()) {
                    Toast.makeText(activity, "还没有收藏夹", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val names = folders.map { "${it.title}（${it.count}）" }.toTypedArray()
                AlertDialog.Builder(activity)
                    .setTitle("选择收藏夹")
                    .setItems(names) { _, which ->
                        val folder = folders[which]
                        thread {
                            val message = try {
                                Api.addFav(prefs, aid, folder.id)
                            } catch (e: Exception) {
                                "失败：${e.message}"
                            }
                            activity.runOnUiThread {
                                Toast.makeText(
                                    activity,
                                    if (message.startsWith("失败")) message else "已加入「${folder.title}」",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDone?.invoke()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }
}
