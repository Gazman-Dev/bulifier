package com.bulifier.core.ui.utils

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

fun View.hideKeyboard() {
    val inputMethodManager =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
}

fun <A : Any?, B : Any?, C : Any?> letAll(a: A?, b: B?, block: (A, B) -> C): C? {
    if (a == null || b == null) {
        return null
    }

    return block(a, b)
}

fun <A : Any?, B : Any?, C : Any?> letAll(a: A?, b: B?, c: C?, block: (A, B, C) -> Unit) {
    if (a == null || b == null || c == null) {
        return
    }

    block(a, b, c)
}

fun <A : Any?, B : Any?, C : Any?, D : Any?> letAll(
    a: A?,
    b: B?,
    c: C?,
    d: D?,
    block: (A, B, C, D) -> Unit
) {
    if (a == null || b == null || c == null || d == null) {
        return
    }

    block(a, b, c, d)
}

fun dpToPx(dp: Float, context: Context): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}

fun getFileType(fileName: String): FileType {
    val extension = fileName.substringAfterLast('.', "").lowercase()

    return when (extension) {
        // Image extensions (compatible with Glide and typical image formats)
        in setOf(
            "jpg",
            "jpeg",
            "png",
            "gif",
            "webp",
            "bmp",
            "ico",
            "heic",
            "heif"
        ) -> FileType.IMAGE

        // Video extensions
        in setOf("mp4", "mkv", "avi", "webm", "mov", "3gp") -> FileType.VIDEO

        // Font extensions
        in setOf("ttf", "otf", "woff", "woff2") -> FileType.FONT

        // PDF
        "pdf" -> FileType.PDF

        // Everything else
        else -> FileType.OTHER
    }
}


enum class FileType {
    IMAGE,
    VIDEO,
    FONT,
    PDF,
    OTHER
}


