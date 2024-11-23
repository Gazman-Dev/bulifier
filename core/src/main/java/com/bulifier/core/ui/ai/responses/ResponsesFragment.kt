package com.bulifier.core.ui.ai.responses

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.bulifier.core.databinding.ResponsesFragmentBinding
import com.bulifier.core.navigation.findNavController
import com.bulifier.core.ui.ai.HistoryViewModel
import com.bulifier.core.ui.core.BaseFragment
import kotlinx.coroutines.launch

class ResponsesFragment : BaseFragment<ResponsesFragmentBinding>() {

    companion object {
        const val KEY_PROMPT_ID = "promptId"
    }

    private val viewModel: HistoryViewModel by activityViewModels()
    private var adapter: ResponsesAdapter? = null
    private var errorMessage: String? = null

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = ResponsesFragmentBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.closeButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.copyButton.setOnClickListener {
            val message = errorMessage ?: adapter?.getContent(binding.viewPager.currentItem)
                ?: return@setOnClickListener
            copyTextToClipboard(message)
            Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val promptId = arguments?.getLong(KEY_PROMPT_ID) ?: -1
            errorMessage = viewModel.getErrorMessages(promptId)
            if(errorMessage != null){
                binding.errorMessage.text = errorMessage
                binding.errorMessage.isVisible = true
                binding.viewPager.isVisible = false
                binding.dotsIndicator.isVisible = false
            }
            else{
                val responses = viewModel.getResponses(promptId)
                adapter = ResponsesAdapter(responses)
                binding.viewPager.adapter = adapter
                binding.dotsIndicator.attachTo(binding.viewPager)
            }
        }
    }

    private fun copyTextToClipboard(text: String) {
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Bulifier", text)
        clipboard.setPrimaryClip(clip)
    }
}