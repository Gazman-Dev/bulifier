package com.bulifier.core.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.bulifier.core.databinding.FragmentAgentTabBinding
import com.bulifier.core.git.GitViewModel
import com.bulifier.core.prefs.PrefStringValue
import com.bulifier.core.security.ProductionSecurityFactory
import com.bulifier.core.security.UiVerifier
import com.bulifier.core.ui.main.MainViewModel
import com.bulifier.core.utils.showToast
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AgentTabFragment : Fragment() {

    private var _binding: FragmentAgentTabBinding? = null
    private val binding get() = _binding!!

    private val agentText = PrefStringValue("agent_text")

    // ViewModels used in your original logic.
    private val viewModel by activityViewModels<HistoryViewModel>()
    private val mainViewModel by activityViewModels<MainViewModel>()
    private val gitViewModel by activityViewModels<GitViewModel>()

    private var modelId: String? = null
    private val security by lazy {
        EntryPointAccessors.fromApplication(
            requireContext().applicationContext,
            ProductionSecurityFactory::class.java
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAgentTabBinding.inflate(inflater, container, false)
        return binding.agentContent
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Prepopulate the text input with the saved agent text.
        binding.aiMessageInput.setText(agentText.flow.value)

        lifecycleScope.launch {
            mainViewModel.appInsets.collectLatest {
                binding.textInputLayout.setPadding(
                    view.paddingLeft, view.paddingTop, view.paddingRight,
                    it.imeInsets.bottom
                )
            }
        }

        // Wire up the send button.
        binding.sendMessageButton.setOnClickListener { buttonView ->
            val uiVerifier: UiVerifier = security.uiVerifier()
            if (!uiVerifier.verifySendAction(view)) {
                // Dismiss the bottom sheet if verification fails.
                (parentFragment as? AgentBottomSheet)?.dismiss()
                return@setOnClickListener
            }
            val message = binding.aiMessageInput.text.toString()
            runIfModelSelected(modelId) { selectedModelId ->
                // Perform git commit if needed.
                if (gitViewModel.autoCommit()) {
                    gitViewModel.commit("Agent: $message")
                }
                val fullPath = mainViewModel.fullPath.value
                viewModel.addAgentMessage(
                    message, selectedModelId,
                    fullPath.path, fullPath.fileName,
                    binding.nativeCodeSwitch.isChecked
                )
                // Clear the message input.
                binding.aiMessageInput.setText("")
                // Dismiss the bottom sheet once the message is sent.
                (parentFragment as? AgentBottomSheet)?.dismiss()
            }
        }

        // Wire up the cancel button to simply dismiss the bottom sheet.
        binding.cancelButton.setOnClickListener {
            (parentFragment as? AgentBottomSheet)?.dismiss()
        }

        // Set up the model spinner with your helper.
        ModelsHelper(
            binding.modelSpinner,
            binding.modelSpinnerContainer,
            viewLifecycleOwner
        ) { selectedModelId ->
            modelId = selectedModelId
        }.setupModels()
    }

    override fun onDestroyView() {
        // Save the current message before the view is destroyed.
        agentText.set(binding.aiMessageInput.text.toString())
        _binding = null
        super.onDestroyView()
    }

    private fun runIfModelSelected(modelId: String?, action: (modelId: String) -> Unit) {
        if (modelId == null) {
            showToast("Please select a model")
        } else {
            action(modelId)
        }
    }
}
