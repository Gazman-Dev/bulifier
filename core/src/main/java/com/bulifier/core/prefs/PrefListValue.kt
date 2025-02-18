package com.bulifier.core.prefs

import com.bulifier.core.prefs.Prefs.pref
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PrefListValue(
    private val key: String
) {
    // Default to an empty list if no value is found
    private val _valueFlow =
        MutableStateFlow(
            pref.getString(key, "")
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