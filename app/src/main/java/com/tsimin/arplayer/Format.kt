package com.tsimin.arplayer

import java.util.Locale

fun fmtCount(value: Long): String {
    return when {
        value >= 100_000_000L -> String.format(Locale.US, "%.1f亿", value / 100_000_000.0)
        value >= 10_000L -> String.format(Locale.US, "%.1f万", value / 10_000.0)
        else -> value.toString()
    }
}
