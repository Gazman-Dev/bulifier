package com.bulifier.core.ui.ai.responses

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bulifier.core.databinding.CoreResponsesFragmentBinding
import com.bulifier.core.ui.ai.HistoryViewModel
import com.bulifier.core.ui.core.BaseFragment
import kotlinx.coroutines.launch

class ResponsesFragment : BaseFragment<CoreResponsesFragmentBinding>() {

    private val args by navArgs<ResponsesFragmentArgs>()
    private val viewModel: HistoryViewModel by activityViewModels()
    private var adapter: ResponsesAdapter? = null

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = CoreResponsesFragmentBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.closeButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.copyButton.setOnClickListener {
            adapter?.let {
                copyTextToClipboard(it.getContent(binding.viewPager.currentItem))
                Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val responses = viewModel.getResponses(args.promptId)
            adapter = ResponsesAdapter(responses)
            binding.viewPager.adapter = adapter
            binding.dotsIndicator.attachTo(binding.viewPager)
        }
    }

    private fun copyTextToClipboard(text: String) {
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Bulifier", text)
        clipboard.setPrimaryClip(clip)
    }
}