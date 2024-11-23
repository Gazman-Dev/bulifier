package com.bulifier.core.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog

fun showTextDialog(
    context: Context,
    hint: String? = null,
    text: String? = null,
    title: String? = null,
    onFileNameChosen: (String) -> Unit
) {
    val builder = AlertDialog.Builder(context)
    title?.let { builder.setTitle(it) }

    val input = EditText(context)
    hint?.let { input.hint = hint }
    text?.let { input.setText(text) }
    builder.setView(input)

    builder.setPositiveButton("OK") { dialog, _ ->
        val fileName = input.text.toString().trim()
        if (fileName.isNotEmpty()) {
            onFileNameChosen(fileName)
        }
        dialog.dismiss()
    }

    builder.setNegativeButton("Cancel") { dialog, _ ->
        dialog.cancel()
    }

    builder.show()
    input.requestFocus()
}

fun showErrorDialog(context: Context, message: String, title: String? = "Error") {
    val builder = AlertDialog.Builder(context)
    builder.setTitle(title)
    builder.setMessage(message)
    builder.setPositiveButton("OK") { dialog, _ ->
        dialog.dismiss()
    }

    builder.show()
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("label", text)
    clipboard.setPrimaryClip(clip)
}
