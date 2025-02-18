package com.bulifier.core.prefs

import com.bulifier.core.prefs.Prefs.pref
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PrefStringValue(
    private val key: String
) {
    // Default to an empty string if no value is found
    private val _valueFlow = MutableStateFlow(pref.getString(key, "") ?: "")

    val flow: StateFlow<String> = _valueFlow

    fun set(value: String) {
        pref.edit().putString(key, value).apply()
        _valueFlow.value = value
    }

    fun clear() {
        pref.edit().remove(key).apply()
        _valueFlow.value = ""
    }
}