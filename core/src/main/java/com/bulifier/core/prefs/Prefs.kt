package com.bulifier.core.prefs

import android.app.Application
import android.content.SharedPreferences
import com.bulifier.core.prefs.Prefs.pref
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
) {
    // Default to an empty string if no value is found
    private val _valueFlow = MutableStateFlow(pref.getString(key, "") ?: "")

    val flow: StateFlow<String> = _valueFlow

    internal fun set(value: String) {
        pref.edit().putString(key, value).apply()
        _valueFlow.value = value
    }
}

class PrefListValue(
    private val key: String
) {
    // Default to an empty list if no value is found
    private val _valueFlow =
        MutableStateFlow(pref.getString(key, "")
            ?.split("\t")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        )

    val flow: StateFlow<List<String>> = _valueFlow

    fun set(value: List<String>?) {
        pref.edit().putString(key, value?.joinToString("\t") ?: "").apply()
        _valueFlow.value = value ?: emptyList()
    }
}

class PrefIntValue(private val key: String) {
    // Default to -1 if no value is found
    private val _valueFlow = MutableStateFlow(pref.getInt(key, -1))

    val flow: StateFlow<Int> = _valueFlow

    internal fun set(value: Int) {
        pref.edit().putInt(key, value).apply()
        _valueFlow.value = value
    }
}

class PrefLongValue(private val key: String) {
    // Default to -1L if no value is found
    private val _valueFlow = MutableStateFlow(pref.getLong(key, -1L))

    val flow: StateFlow<Long> = _valueFlow

    internal fun set(value: Long) {
        pref.edit().putLong(key, value).apply()
        _valueFlow.value = value
    }
}
