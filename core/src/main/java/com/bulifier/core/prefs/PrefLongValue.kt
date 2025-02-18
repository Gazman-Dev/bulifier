package com.bulifier.core.prefs

import com.bulifier.core.prefs.Prefs.pref
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PrefLongValue(private val key: String) {
    // Default to -1L if no value is found
    private val _valueFlow = MutableStateFlow(pref.getLong(key, -1L))

    val flow: StateFlow<Long> = _valueFlow

    fun set(value: Long) {
        pref.edit().putLong(key, value).apply()
        _valueFlow.value = value
    }
}