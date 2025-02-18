package com.bulifier.core.ui.utils

import com.bulifier.core.prefs.AppSettings
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

// Function to get the class name from an instance
fun getClassName(instance: Any): String {
    return instance::class.qualifiedName ?: throw IllegalArgumentException("Class name not found")
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> createInstance(className: String): T? {
    return try {
        val kClass = Class.forName(className).kotlin as KClass<T>
        kClass.createInstance()
    } catch (e: Exception) {
        AppSettings.appLogger.e(e)
        null
    }
}