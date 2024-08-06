package com.bulifier.core.ui.content

import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.bulifier.core.databinding.CoreFileContentFragmentBinding
import com.bulifier.core.ui.main.MainViewModel

class FileContentFragment : Fragment() {

    private lateinit var binding: CoreFileContentFragmentBinding
    private val viewModel by activityViewModels<MainViewModel>()
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
        viewModel.openedFile.observe(viewLifecycleOwner){
            binding.textBox.setText(it?.content)
        }
        binding.textBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.updateFileContent(s.toString())
            }
        })
    }

}