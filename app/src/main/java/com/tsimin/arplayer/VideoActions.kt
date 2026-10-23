package com.tsimin.arplayer

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import kotlin.concurrent.thread

object VideoActions {

    fun show(activity: Activity, prefs: Prefs, bvid: String, onDone: (() -> Unit)? = null) {
        thread {
            try {
                val info = Api.videoInfo(prefs, bvid)
                val followed = if (info.mid > 0) {
                    try {
                        Api.relationState(prefs, info.mid)
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    false
                }
                val liked = try {
                    Api.hasLike(prefs, bvid)
                } catch (_: Exception) {
                    false
                }
                val coins = try {
                    Api.coins(prefs, bvid)
                } catch (_: Exception) {
                    0
                }
                activity.runOnUiThread {
                    dialog(activity, prefs, bvid, info, followed, liked, coins, onDone)
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "获取视频信息失败：${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun dialog(
        activity: Activity,
        prefs: Prefs,
        bvid: String,
        info: VideoInfo,
        followed: Boolean,
        liked: Boolean,
        coins: Int,
        onDone: (() -> Unit)?
    ) {
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> String>()

        if (info.mid > 0) {
            labels.add(if (followed) "取消关注 ${info.uname}" else "关注 ${info.uname}")
            actions.add { Api.setFollow(prefs, info.mid, !followed) }
        }

        labels.add(if (liked) "取消点赞" else "点赞")
        actions.add { Api.setLike(prefs, bvid, !liked) }

        if (coins < 2) {
            labels.add(if (coins == 0) "投币 1 枚" else "再投 1 枚（已投 $coins）")
            actions.add { Api.addCoin(prefs, bvid, 1) }
        } else {
            labels.add("已投币 2 枚")
            actions.add { "已经投满 2 枚了" }
        }

        labels.add("收藏到默认收藏夹")
        actions.add { Api.addFav(prefs, info.aid) }

        AlertDialog.Builder(activity)
            .setTitle(info.title.take(40))
            .setItems(labels.toTypedArray()) { _, which ->
                thread {
                    val message = try {
                        actions[which]()
                    } catch (e: Exception) {
                        "失败：${e.message}"
                    }
                    activity.runOnUiThread {
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                        onDone?.invoke()
                    }
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }
}
