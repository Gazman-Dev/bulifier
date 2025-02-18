package com.bulifier.core.prefs

import com.bulifier.core.prefs.Prefs.pref
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PrefBooleanValue(private val key: String, defaultValue: Boolean = false) {
    // Default to -1L if no value is found
    private val _valueFlow = MutableStateFlow(pref.getBoolean(key, defaultValue))

    val flow: StateFlow<Boolean> = _valueFlow

    fun set(value: Boolean) {
        pref.edit().putBoolean(key, value).apply()
        _valueFlow.value = value
    }
}