package com.bulifier.core.ui.content

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
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
    private val viewModel: MainViewModel by activityViewModels<MainViewModel>()
    private val historyViewModel by activityViewModels<HistoryViewModel>()

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
        val textWatcher = ContentTextWatcher(
            binding.textBox,
            viewModel,
            viewLifecycleOwner
        )
        binding.ai.setOnClickListener {
            viewModel.fullPath.value.run {
                historyViewModel.createNewAiJob(path, fileName)
            }
            findNavController().navigate(R.id.aiHistoryFragment)
        }
        binding.textBox.movementMethod = ScrollingMovementMethod()
        binding.textBoxView.movementMethod = ScrollingMovementMethod()
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fileContent.collect {
                Log.d("FileContentFragment", "UI: updated from model\n\n$it\n\n")
                textWatcher.update(it)
                binding.textBoxView.text = it
            }
        }

        binding.editMode.setOnCheckedChangeListener { _, isChecked ->
            binding.horizontalScrollViewEditText.isVisible = isChecked
            binding.horizontalScrollViewTextView.isVisible = !isChecked
            binding.textBoxView.isVisible = !isChecked

            if (isChecked) {
                textWatcher.start(binding.textBoxView.text.toString())
            } else {
                binding.textBoxView.text = binding.textBox.text
                textWatcher.stop()
            }
        }

        setupDoubleTop()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDoubleTop() {
        binding.textBox.setOnTouchListener(
            object : View.OnTouchListener {
                private val gestureDetector = GestureDetector(
                    requireContext(),
                    object : GestureDetector.SimpleOnGestureListener() {

                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            binding.editMode.isChecked = !binding.editMode.isChecked
                            return true
                        }
                    })

                override fun onTouch(v: View?, event: MotionEvent) =
                    gestureDetector.onTouchEvent(event)
            })
    }

}