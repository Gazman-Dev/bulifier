package com.bulifier.core.prefs

import android.app.Application

class BulyApp : Application() {
    override fun onCreate() {
        Prefs.initialize(this)
        super.onCreate()
    }
}