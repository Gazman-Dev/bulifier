package com.bulifier.core.prefs

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import com.bulifier.core.prefs.Prefs.pref

object Prefs {
    internal lateinit var pref: SharedPreferences

    val projectId by lazy { PrefLongValue("projectId") }
    val projectName by lazy { PrefStringValue("project_v10") }
    val path by lazy { PrefStringValue("path_v1") }
    val models by lazy { PrefListValue("models") }

    fun initialize(bulyApp: Application) {
        pref = bulyApp.getSharedPreferences("buly", 0)
    }

    fun put(key: String, value: String) {
        pref.edit().putString(key, value).apply()
    }

    fun get(key: String): String? {
        return pref.getString(key, null)
    }
}

class PrefStringValue(
    private val key: String
) :
    LiveData<String>() {

    init {
        pref.getString(key, null)?.let {
            value = it
        }
    }

    internal fun set(value: String) {
        pref.edit().putString(key, value).apply()
        super.setValue(value)
    }
}

class PrefListValue(
    private val key: String
) : LiveData<List<String>>() {

    init {
        pref.getString(key, null)?.let {
            // there are no tabs on the mobile keyboard so less likely to collide
            value = it.split("\t")
        }
    }

    internal fun set(value: List<String>?) {
        pref.edit().putString(key, value?.joinToString("\t")).apply()
        super.setValue(value)
    }
}

class PrefIntValue(private val key: String) :
    LiveData<Int>() {

    init {
        val v = pref.getInt(key, -1)
        if (v != -1) {
            value = v
        }
    }

    internal fun set(value: Int) {
        pref.edit().putInt(key, value).apply()
        super.setValue(value)
    }
}

class PrefLongValue(private val key: String) :
    LiveData<Long>() {

    init {
        val v = pref.getLong(key, -1)
        if (v != -1L) {
            value = v
        }
    }

    internal fun set(value: Long) {
        pref.edit().putLong(key, value).apply()
        super.setValue(value)
    }
}