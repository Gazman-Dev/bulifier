package com.bulifier.core.ui.content

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bulifier.core.R
import com.bulifier.core.databinding.CoreFileContentFragmentBinding
import com.bulifier.core.ui.ai.HistoryViewModel
import com.bulifier.core.ui.main.MainViewModel
import kotlinx.coroutines.launch

class FileContentFragment : Fragment() {

    private lateinit var binding: CoreFileContentFragmentBinding
    private val viewModel: MainViewModel by activityViewModels()
    private val historyViewModel: HistoryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = CoreFileContentFragmentBinding.inflate(layoutInflater, container, false).run {
        binding = this
        root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize your text watcher if needed
        val textWatcher = ContentTextWatcher(
            binding.textBox,
            viewModel,
            viewLifecycleOwner
        )

        // AI button click listener
        binding.ai.setOnClickListener {
            viewModel.fullPath.value.run {
                historyViewModel.createNewAiJob(path, fileName)
            }
            findNavController().navigate(R.id.aiHistoryFragment)
        }

        // Update textBox with content from ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fileContent.collect { content ->
                Log.d("FileContentFragment", "UI: updated from model\n\n$content\n\n")
                textWatcher.update(content)
            }
        }

        // Handle edit mode toggle
        binding.editMode.setOnCheckedChangeListener { _, isChecked ->
            binding.textBox.isEditable = isChecked

            if (isChecked) {
                textWatcher.start()
                binding.textBox.requestFocus()
            } else {
                textWatcher.stop()
            }
        }
        binding.textBox.isEditable = binding.editMode.isChecked

        // Setup double-tap gesture to toggle edit mode
        setupDoubleTap()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDoubleTap() {
        binding.textBox.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private val gestureDetector by lazy {
        GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    binding.editMode.isChecked = !binding.editMode.isChecked
                    return true
                }
            }
        )
    }
}
