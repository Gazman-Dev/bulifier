package com.bulifier.core.ui.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator

fun getScreenHeight(context: Context): Int {
    val displayMetrics = DisplayMetrics()
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    windowManager.defaultDisplay.getMetrics(displayMetrics)

    // Calculate the status bar height
    val statusBarHeightResId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    val statusBarHeight = if (statusBarHeightResId > 0) {
        context.resources.getDimensionPixelSize(statusBarHeightResId)
    } else {
        0
    }

    // Subtract the status bar height from the total screen height to get the usable screen height
    return displayMetrics.heightPixels - statusBarHeight
}

fun View.grow(targetHeight: Int, callback: (() -> Unit)? = null) {
    val valueAnimator = ValueAnimator.ofInt(0, targetHeight)
    valueAnimator.addUpdateListener { animation ->
        val animatedValue = animation.animatedValue as Int
        layoutParams.height = animatedValue
        layoutParams = layoutParams
    }
    valueAnimator.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            callback?.invoke()
        }
    })
    valueAnimator.duration = 500 // Duration in milliseconds
    valueAnimator.interpolator = AccelerateDecelerateInterpolator()
    valueAnimator.start()
}

fun View.hide() {
    val currentHeight = height
    val valueAnimator = ValueAnimator.ofInt(currentHeight, 0)
    valueAnimator.addUpdateListener { animation ->
        val animatedValue = animation.animatedValue as Int
        layoutParams.height = animatedValue
        layoutParams = layoutParams
    }
    valueAnimator.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            visibility = View.GONE
        }
    })
    valueAnimator.duration = 500 // Duration in milliseconds
    valueAnimator.interpolator = AccelerateDecelerateInterpolator()
    valueAnimator.start()
}