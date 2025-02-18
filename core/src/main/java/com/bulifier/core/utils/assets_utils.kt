package com.bulifier.core.utils

import android.content.Context
import android.os.Looper
import android.widget.Toast
import com.bulifier.core.prefs.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun loadAssets(context: Context, path: String): String {
    val project = AppSettings.project.value

    val path = if (project.template?.isNotBlank() == true) {
        "templates/${project.template}/$path"
    } else {
        path
    }
    return withContext(Dispatchers.IO) {
        context.assets.open(path).bufferedReader().readText()
    }
}

fun showToast(message: String) {
    try {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            toast(message)
            return
        }
        AppSettings.scope.launch {
            withContext(Dispatchers.Main) {
                toast(message)
            }
        }
    }
    catch (e: Exception) {
        AppSettings.appLogger.e(e)
    }
}

private fun toast(message: String) {
    Toast.makeText(AppSettings.appContext, message, Toast.LENGTH_SHORT).show()
}