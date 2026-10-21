package com.tsimin.arplayer

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("arplayer", Context.MODE_PRIVATE)

    var sessdata: String
        get() = sp.getString("sessdata", "").orEmpty()
        set(v) { sp.edit().putString("sessdata", v).apply() }

    var biliJct: String
        get() = sp.getString("bili_jct", "").orEmpty()
        set(v) { sp.edit().putString("bili_jct", v).apply() }

    var dedeUserId: String
        get() = sp.getString("dede_user_id", "").orEmpty()
        set(v) { sp.edit().putString("dede_user_id", v).apply() }

    var dedeUserIdCkMd5: String
        get() = sp.getString("dede_user_id_ckmd5", "").orEmpty()
        set(v) { sp.edit().putString("dede_user_id_ckmd5", v).apply() }

    var buvid3: String
        get() = sp.getString("buvid3", "").orEmpty()
        set(v) { sp.edit().putString("buvid3", v).apply() }

    var buvid4: String
        get() = sp.getString("buvid4", "").orEmpty()
        set(v) { sp.edit().putString("buvid4", v).apply() }

    var mid: Long
        get() = sp.getLong("mid", 0L)
        set(v) { sp.edit().putLong("mid", v).apply() }

    var uname: String
        get() = sp.getString("uname", "").orEmpty()
        set(v) { sp.edit().putString("uname", v).apply() }

    var selectedUps: Set<Long>
        get() = sp.getStringSet("selected_ups", null)
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
        set(v) {
            sp.edit().putStringSet("selected_ups", v.map { it.toString() }.toSet()).apply()
        }

    var tags: Set<String>
        get() = sp.getStringSet("tags", null)?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("tags", v).apply() }

    val isLoggedIn: Boolean
        get() = sessdata.isNotEmpty() && dedeUserId.isNotEmpty()

    fun clear() {
        sp.edit().clear().apply()
    }

    fun watched(): Set<String> =
        sp.getStringSet("watched", null)?.toSet() ?: emptySet()

    fun isWatched(bvid: String): Boolean = bvid in watched()

    fun markWatched(bvid: String) {
        val next = watched().toMutableSet()
        if (next.add(bvid)) {
            sp.edit().putStringSet("watched", next).apply()
        }
    }
}
