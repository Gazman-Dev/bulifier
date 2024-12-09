package com.bulifier.core.ui.content

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.bulifier.core.databinding.FileContentFragmentBinding
import com.bulifier.core.ui.main.MainViewModel
import com.bulifier.core.utils.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

private val mainId = AtomicInteger(1)

class FileContentFragment : Fragment() {

    private var textWatcher: ContentTextWatcher? = null
    private lateinit var binding: FileContentFragmentBinding
    private val viewModel: MainViewModel by activityViewModels()
    private val fragmentId = mainId.incrementAndGet()
    private val logger = Logger("FileContentFragment($fragmentId)")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FileContentFragmentBinding.inflate(layoutInflater, container, false).run {
        binding = this
        root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logger.d("onViewCreated")

        ViewCompat.setOnApplyWindowInsetsListener(binding.contentFrame) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.setPadding(
                systemBarInsets.left,
                systemBarInsets.top,
                systemBarInsets.right,
                imeInsets.bottom
            )
            insets
        }

        // Initialize your text watcher if needed
        val textWatcher = ContentTextWatcher(
            binding.textBox,
            viewModel,
            viewLifecycleOwner
        )
        this.textWatcher = textWatcher

        // Update textBox with content from ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fileContent.collectLatest { content ->
                logger.d("UI: updated from model\n\n$content\n\n")
                try {
                    textWatcher.update(content)
                } catch (e: Exception) {
                    logger.e("Error updating text watcher", e)
                }
            }
        }

        // Handle edit mode toggle
        binding.editMode.setOnCheckedChangeListener { _, isChecked ->
            try {
                binding.textBox.isEditable = isChecked

                if (isChecked) {
                    textWatcher.start()
                    binding.textBox.requestFocus()
                    logger.i("EditModeEnabled")
                } else {
                    textWatcher.stop()
                    logger.i("EditModeDisabled")
                }
            } catch (e: Exception) {
                logger.e("Error toggling edit mode", e)
            }
        }
        binding.textBox.isEditable = binding.editMode.isChecked

        // Setup double-tap gesture to toggle edit mode
        setupDoubleTap()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDoubleTap() {
        try {
            binding.textBox.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }
            logger.i("DoubleTapSetup")
        } catch (e: Exception) {
            logger.e("Error setting up double tap gesture", e)
        }
    }

    private val gestureDetector by lazy {
        GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    try {
                        binding.editMode.isChecked = !binding.editMode.isChecked
                        logger.i("DoubleTapDetected")
                        return true
                    } catch (e: Exception) {
                        logger.e("Error handling double tap", e)
                        return false
                    }
                }
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (!this::binding.isInitialized) {
            return
        }
        val textBox: ScrollableEditText = binding.textBox

        val cursorPosition = textBox.selectionStart
        outState.putInt("cursor_position", cursorPosition)
        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        logger.d("onViewStateRestored")
        // Ensure you're on the main thread
        viewLifecycleOwner.lifecycleScope.launch {
            delay(1)
            val content = viewModel.fileContent.value
            logger.d("UI: updated from model after state restored\n\n$content\n\n")
            textWatcher?.update(content)
            val cursorPosition = savedInstanceState?.getInt("cursor_position", -1)
            if (cursorPosition != -1 && cursorPosition != null && cursorPosition < content.length) {
                binding.textBox.setSelection(cursorPosition)
            }
        }
    }
}
