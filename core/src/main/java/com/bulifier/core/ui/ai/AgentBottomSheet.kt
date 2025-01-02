package com.bulifier.core.ui.ai

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.bulifier.core.databinding.AgentBottomSheetBinding
import com.bulifier.core.prefs.PrefStringValue
import com.bulifier.core.security.ProductionSecurityFactory
import com.bulifier.core.security.UiVerifier
import com.bulifier.core.ui.main.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.EntryPointAccessors
import kotlin.getValue

class AgentBottomSheet : BottomSheetDialogFragment() {

    private val agentText = PrefStringValue("agent_text")
    private lateinit var binding: AgentBottomSheetBinding
    private val viewModel by activityViewModels<HistoryViewModel>()
    private val mainViewModel by activityViewModels<MainViewModel>()
    private val security by lazy {
        EntryPointAccessors.fromApplication(
            binding.root.context.applicationContext,
            ProductionSecurityFactory::class.java
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = AgentBottomSheetBinding.inflate(inflater, container, false)
        return binding.agentBottomSheet
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var modelId: String? = null
        binding.aiMessageInput.setText(agentText.flow.value)

        binding.sendMessageButton.setOnClickListener {
            val uiVerifier: UiVerifier = security.uiVerifier()
            if (!uiVerifier.verifySendAction(view)) {
                dismiss()
                return@setOnClickListener
            }
            val message = binding.aiMessageInput.text.toString()
            runIfModelSelected(modelId) {
                val fullPath = mainViewModel.fullPath.value
                viewModel.addAgentMessage(message, it, fullPath.path, fullPath.fileName)
                binding.aiMessageInput.setText("")
                dismiss()
            }
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        ModelsHelper(
            binding.modelSpinner,
            binding.modelSpinnerContainer,
            viewLifecycleOwner
        ) {
            modelId = it
        }.setupModels()
    }

    override fun onDismiss(dialog: DialogInterface) {
        agentText.set(binding.aiMessageInput.text.toString())
        super.onDismiss(dialog)
    }

    private fun runIfModelSelected(modelId: String?, action: (modelId: String) -> Unit) {
        if (modelId == null) {
            Toast.makeText(requireContext(), "Please select a model", Toast.LENGTH_SHORT).show()
        } else {
            action(modelId)
        }
    }
}
