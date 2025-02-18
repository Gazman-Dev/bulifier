package com.bulifier.core.prefs

import com.bulifier.core.prefs.Prefs.pref
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PrefIntValue(private val key: String) {
    // Default to -1 if no value is found
    private val _valueFlow = MutableStateFlow(pref.getInt(key, -1))

    val flow: StateFlow<Int> = _valueFlow

    fun set(value: Int) {
        pref.edit().putInt(key, value).apply()
        _valueFlow.value = value
    }
}