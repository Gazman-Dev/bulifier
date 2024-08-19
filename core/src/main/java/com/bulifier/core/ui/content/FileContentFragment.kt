package com.bulifier.core.ui.content

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.bulifier.core.R
import com.bulifier.core.databinding.CoreFileContentFragmentBinding
import com.bulifier.core.ui.ai.HistoryViewModel
import com.bulifier.core.ui.main.MainViewModel

class FileContentFragment : Fragment() {

    private lateinit var binding: CoreFileContentFragmentBinding
    private val viewModel by activityViewModels<MainViewModel>()
    private var dirty = false
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
        binding.ai.setOnClickListener {
            viewModel.fullPath.value?.run {
                historyViewModel.createNewAiJob(path, fileName)
            }
            findNavController().navigate(R.id.aiHistoryFragment)
        }
        var text = ""
        binding.textBox.movementMethod = ScrollingMovementMethod()
        binding.textBoxView.movementMethod = ScrollingMovementMethod()
        viewModel.fileContent.observe(viewLifecycleOwner) {
            if(text != it) {
                binding.textBox.setText(it)
                binding.textBoxView.text = it
            }
        }
        binding.textBox.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: android.text.Editable?) {
                val newText = s.toString()
                dirty = text != newText
                text = newText
            }
        })

        binding.editMode.setOnCheckedChangeListener { _, isChecked ->
            binding.horizontalScrollViewEditText.isVisible = isChecked
            binding.horizontalScrollViewTextView.isVisible = !isChecked
            binding.textBoxView.text = binding.textBox.text
        }

        setupDoubleTop()

        Ticker(viewLifecycleOwner) {
            if (dirty) {
                viewModel.updateFileContent(binding.textBox.text.toString())
            }
            dirty = false
        }

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