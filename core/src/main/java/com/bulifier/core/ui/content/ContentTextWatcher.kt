package com.bulifier.core.ui.content

import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.bulifier.core.ui.main.MainViewModel
import com.bulifier.core.ui.utils.hideKeyboard

class ContentTextWatcher(
    private val textBox: EditText,
    private val viewModel: MainViewModel,
    lifecycleOwner: LifecycleOwner
) : DefaultLifecycleObserver {
    private var startRequested = false
    private var dirty = false
    private var systemText = ""
    private val ticker = Ticker(lifecycleOwner) {
        Log.d("FileContentFragment", "UI: tick")
        updateContent()
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    private val watcher = object : TextWatcher {

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

        }

        override fun afterTextChanged(s: android.text.Editable?) {
            val newText = s.toString()
            if (newText == systemText) {
                return
            }
            dirty = true
            Log.d("FileContentFragment", "UI: Text updated ${newText.length}")
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        verifyWatcher()
    }

    override fun onPause(owner: LifecycleOwner) {
        verifyWatcher()
        super.onPause(owner)
    }

    private fun verifyWatcher() {
        if (startRequested) {
            Log.d("FileContentFragment", "UI: watcher added")
            textBox.addTextChangedListener(watcher)
        } else {
            Log.d("FileContentFragment", "UI: watcher removed")
            textBox.removeTextChangedListener(watcher)
            textBox.hideKeyboard()
        }
    }

    fun start() {
        startRequested = true
        verifyWatcher()
    }

    fun stop() {
        startRequested = false
        textBox.removeTextChangedListener(watcher)
        verifyWatcher()
    }

    private fun updateContent() {
        if (dirty && startRequested) {
            dirty = false
            val content = textBox.text.toString()
            viewModel.updateFileContent(content)
            Log.d("FileContentFragment", toString() + "\nUI: viewModel updated\n\n$content\n\n")
        }
    }

    fun update(text: String) {
        ticker.reset()
        systemText = text
        textBox.setText(text)
    }
}