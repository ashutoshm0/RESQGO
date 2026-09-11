package com.example.resqgo.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * With targetSdk 35+ Android lays every window out edge-to-edge, so content
 * slides under the status bar and the gesture pill. Add those insets back as
 * padding on whichever container should stay clear of them.
 */
fun View.applySystemBarPadding(top: Boolean = false, bottom: Boolean = false) {
    val basePaddingTop = paddingTop
    val basePaddingBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updatePadding(
            top = if (top) basePaddingTop + bars.top else v.paddingTop,
            bottom = if (bottom) basePaddingBottom + bars.bottom else v.paddingBottom
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
