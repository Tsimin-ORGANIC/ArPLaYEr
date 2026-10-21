package com.tsimin.arplayer

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val label = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            gravity = Gravity.CENTER
        }
        setContentView(label)
    }
}
