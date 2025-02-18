package com.bulifier.core.prefs

import android.content.SharedPreferences

object Prefs {
    internal lateinit var pref: SharedPreferences

    fun put(key: String, value: String) {
        pref.edit().putString(key, value).apply()
    }

    fun delete(key: String) {
        pref.edit().remove(key).apply()
    }

    fun get(key: String): String? {
        return pref.getString(key, null)
    }
}