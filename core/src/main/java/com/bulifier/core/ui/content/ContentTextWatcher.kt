package com.bulifier.core.ui.content

import android.text.TextWatcher
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.bulifier.core.ui.main.MainViewModel
import com.bulifier.core.ui.utils.hideKeyboard
import com.bulifier.core.utils.Logger

class ContentTextWatcher(
    private val textBox: ScrollableEditText,
    private val viewModel: MainViewModel,
    lifecycleOwner: LifecycleOwner
) : DefaultLifecycleObserver {
    private var startRequested = false
    private var dirty = false
    private var systemText = ""
    private val ticker = Ticker(lifecycleOwner) {
        updateContent()
    }
    private val logger = Logger("ContentTextWatcher")

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    private val watcher = object : TextWatcher {

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            // No action required
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            // No action required
        }

        override fun afterTextChanged(s: android.text.Editable?) {
            val newText = s.toString()
            if (newText == systemText) {
                return
            }
            dirty = true
            logger.d("Text updated, length: ${newText.length}")
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        logger.d("ContentTextWatcherOnResume")
        verifyWatcher()
    }

    override fun onPause(owner: LifecycleOwner) {
        logger.d("ContentTextWatcherOnPause")
        verifyWatcher()
        super.onPause(owner)
    }

    private fun verifyWatcher() {
        if (startRequested) {
            logger.d("Watcher added to textBox")
            textBox.addTextChangedListener(watcher)
        } else {
            logger.d("Watcher removed from textBox")
            textBox.removeTextChangedListener(watcher)
            textBox.hideKeyboard()
        }
    }

    fun start() {
        startRequested = true
        logger.d("ContentTextWatcherStart")
        verifyWatcher()
    }

    fun stop() {
        startRequested = false
        logger.d("ContentTextWatcherStop")
        textBox.removeTextChangedListener(watcher)
        verifyWatcher()
    }

    private fun updateContent() {
        if (dirty && startRequested) {
            dirty = false
            val content = textBox.text.toString()
            logger.d("Updating ViewModel with new content")
            viewModel.updateFileContent(content)
            logger.d("Content updated in ViewModel: $content")
        }
    }

    fun update(text: String) {
        ticker.reset()
        systemText = text
        if (textBox.text.toString() != text) {
            val cursorPosition = textBox.selectionStart
            textBox.setText(text)
            if (cursorPosition <= text.length) {
                textBox.setSelection(cursorPosition)
            } else {
                textBox.setSelection(text.length) // Prevent invalid position
            }
            logger.d("TextBox content updated programmatically: $text")
        }
    }
}