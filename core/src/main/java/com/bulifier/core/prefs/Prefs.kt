package com.bulifier.core.prefs

import android.content.Context
import android.content.SharedPreferences
import com.bulifier.core.db.Project
import com.bulifier.core.prefs.Prefs.pref
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object Prefs {
    internal lateinit var pref: SharedPreferences

    fun updateProject(project: Project) {
        projectId.set(project.projectId)
        projectName.set(project.projectName)
        projectDetails.set(project.projectDetails ?: "")
        path.set("")
    }

    val projectId by lazy { PrefLongValue("projectId") }
    val projectName by lazy { PrefStringValue("project_v10") }
    val projectDetails by lazy { PrefStringValue("project_details") }
    val path by lazy { PrefStringValue("path_v1") }
    val models by lazy { PrefListValue("models") }

    fun clear() {
        projectId.set(-1)
        projectName.set("")
        projectDetails.set("")
        path.set("")
    }

    fun initialize(context: Context) {
        pref = context.getSharedPreferences("buly", 0)
    }

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

    fun set(value: Int) {
        pref.edit().putInt(key, value).apply()
        _valueFlow.value = value
    }
}

class PrefLongValue(private val key: String) {
    // Default to -1L if no value is found
    private val _valueFlow = MutableStateFlow(pref.getLong(key, -1L))

    val flow: StateFlow<Long> = _valueFlow

    fun set(value: Long) {
        pref.edit().putLong(key, value).apply()
        _valueFlow.value = value
    }
}
