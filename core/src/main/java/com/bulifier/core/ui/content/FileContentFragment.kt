package com.bulifier.core.ui.content

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.bulifier.core.databinding.FileContentFragmentBinding
import com.bulifier.core.prefs.AppSettings
import com.bulifier.core.prefs.AppSettings.SETTINGS_BULIFIER
import com.bulifier.core.ui.main.MainViewModel
import com.bulifier.core.utils.Logger
import com.bulifier.core.utils.showToast
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
    private val backCallback = object : OnBackPressedCallback(true) { // Initially disabled
        override fun handleOnBackPressed() {
            if (viewModel.fullScreenMode.value) {
                viewModel.fullScreenMode.value = false
            } else {
                viewModel.closeFile()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FileContentFragmentBinding.inflate(layoutInflater, container, false).run {
        binding = this
        root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, null)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.openedFile.collect {
                backCallback.isEnabled = it != null
            }
        }
        logger.d("onViewCreated")

        binding.wrapMode.isChecked = viewModel.wrapping.flow.value
        binding.wrapMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.wrapping.set(isChecked)
            binding.textBox.setIsWrapped(isChecked)
        }

        binding.textBox.setIsWrapped(binding.wrapMode.isChecked)

        ViewCompat.setOnApplyWindowInsetsListener(binding.contentFrame) { view, insets ->
            val imeInsets: Insets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.setPadding(
                0,
                0,
                0,
                0.coerceAtLeast(imeInsets.bottom - systemBars.bottom)
            )

            binding.switchesRow.isVisible = imeInsets.bottom == 0

            viewModel.setInsets(imeInsets, systemBars)

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

        binding.applySettings.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                AppSettings.reloadSettings()
                showToast("Settings applied")
            }
        }
        binding.resetDefaults.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                AppSettings.reloadSettings(true)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fullPath.collectLatest {
                binding.applySettings.isVisible = it.fullPath == SETTINGS_BULIFIER
                binding.resetDefaults.isVisible = it.fullPath == SETTINGS_BULIFIER
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fullScreenMode.collect {
                binding.switchesRow.isVisible = !it
            }
        }

        // Setup double-tap gesture to toggle edit mode
        setupDoubleTap()
    }

    private fun setupDoubleTap() {
        binding.textBox.setOnDoubleTapListener {
            try {
                viewModel.fullScreenMode.value = !viewModel.fullScreenMode.value
                logger.i("DoubleTapDetected")
            } catch (e: Exception) {
                logger.e("Error handling double tap", e)
            }
        }
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
