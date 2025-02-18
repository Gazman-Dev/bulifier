package com.bulifier.core.prefs

import com.bulifier.core.prefs.Prefs.pref
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PrefObjectValue<T : Any>(
    private val key: String,
    private val type: Class<T>
) {
    // Default to an empty string if no value is found
    private val _valueFlow = MutableStateFlow(parseOrNull(pref.getString(key, "")))

    private fun parseOrNull(s: String?): T? {
        if (s.isNullOrEmpty()) return null
        return try {
            gson.fromJson(s, type)
        } catch (e: Exception) {
            null
        }
    }

    private val gson = Gson()

    val flow: StateFlow<T?> = _valueFlow

    fun set(value: T) {
        pref.edit().putString(key, gson.toJson(value)).apply()
        _valueFlow.value = value
    }

    fun clear() {
        pref.edit().remove(key).apply()
        _valueFlow.value = null
    }


}