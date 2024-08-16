package com.bulifier.core.ui.content

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class Ticker(
    private val lifecycleOwner: LifecycleOwner,
    private val intervalMillis: Long = 1000L,
    private val onTick: () -> Unit
) : DefaultLifecycleObserver {

    private val handler = Handler(Looper.getMainLooper())
    private val tickerRunnable = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, intervalMillis)
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        handler.post(tickerRunnable)
    }

    override fun onPause(owner: LifecycleOwner) {
        handler.removeCallbacks(tickerRunnable)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        handler.removeCallbacks(tickerRunnable)
        lifecycleOwner.lifecycle.removeObserver(this)
    }
}

