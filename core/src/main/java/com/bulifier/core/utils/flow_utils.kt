package com.bulifier.core.utils

import com.bulifier.core.prefs.AppSettings

inline fun <T> T.ifNull(block: () -> Unit): T {
    if (this == null) {
        block()
    }
    return this
}

fun fullPath(path: String, fileName: String) = path.removePrefix(
    AppSettings.project.value.projectName
).run {
    if (isEmpty()) fileName else "$this/$fileName"
}

