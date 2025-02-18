package com.bulifier.core.utils

import android.os.Bundle
import android.util.Log

// Define interfaces to abstract Firebase dependencies
interface AnalyticsInterface {
    fun logEvent(name: String, params: Bundle?)
    fun setUserProperty(name: String, value: String?)
}

interface CrashlyticsInterface {
    fun recordException(throwable: Throwable)
    fun setCustomKey(key: String, value: String)
}

object L {
    var analytics: AnalyticsInterface? = null
        private set
    var crashlytics: CrashlyticsInterface? = null
        private set

    /**
     * Initializes the logger with implementations of Analytics and Crashlytics.
     * This abstracts the dependencies from the L object.
     */
    fun init(analytics: AnalyticsInterface?, crashlytics: CrashlyticsInterface?) {
        L.analytics = analytics
        L.crashlytics = crashlytics
    }

    fun setProperty(name: String, value: String) {
        analytics?.setUserProperty(name, value)
        crashlytics?.setCustomKey(name, value)
    }
}

/**
 * Logger class for logging messages with a specific tag.
 * Use Bulifier Logger GPT to add logs -> https://chatgpt.com/g/g-673df8f010ec819186e10b0306359c90-bulifier-logger
 */
class Logger(val tag: String) {

    /**
     * Logs a debug message.
     */
    fun d(message: String) {
        Log.d(tag, message)
    }

    /**
     * Logs a debug message.
     */
    fun w(message: String) {
        Log.w(tag, message)
    }

    /**
     * Logs analytics event and info log.
     */
    fun i(eventName: String, params: Bundle? = null) {
        Log.i(tag, eventName)
        L.analytics?.logEvent(eventName, params)
    }

    /**
     * Logs an error message with an optional throwable.
     * This method is inlined to capture the correct stack trace.
     */
    inline fun e(message: String) {
        val exception = Exception(message)
        Log.e(tag, message, exception)
        L.crashlytics?.recordException(exception)
    }

    /**
     * Logs an error message with an optional throwable.
     * This method is inlined to capture the correct stack trace.
     */
    fun e(message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
        L.crashlytics?.recordException(throwable)
    }

    /**
     * Logs an error message with an optional throwable.
     * This method is inlined to capture the correct stack trace.
     */
    fun e(throwable: Throwable) {
        Log.e(tag, null, throwable)
        L.crashlytics?.recordException(throwable)
    }
}
