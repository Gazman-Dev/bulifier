package com.bulifier.core.ui.ai.history_adapter

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bulifier.core.databinding.SelectedHistoryItemBinding
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.db.HistoryStatus
import com.bulifier.core.navigation.findNavController
import com.bulifier.core.schemas.SchemaModel
import com.bulifier.core.security.ProductionSecurityFactory
import com.bulifier.core.security.UiVerifier
import com.bulifier.core.ui.ai.HistoryViewModel
import com.bulifier.core.ui.ai.ModelsHelper
import com.bulifier.core.ui.ai.responses.ResponsesFragment
import com.bulifier.core.ui.utils.hideKeyboard
import com.bulifier.core.ui.utils.letAll
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectedHistoryViewHolder(
    private val viewModel: HistoryViewModel,
    private val historyList: RecyclerView,
    private val binding: SelectedHistoryItemBinding,
    viewLifecycleOwner: LifecycleOwner
) :
    BaseHistoryViewHolder(binding.root, viewModel, viewLifecycleOwner) {
    private var schemas: Array<String>? = null

    private val security by lazy {
        EntryPointAccessors.fromApplication(
            binding.root.context.applicationContext,
            ProductionSecurityFactory::class.java
        )
    }
    private var ignoreTextUpdates = false
    private var historyItem: HistoryItem? = null
    private val modelsHelper by lazy {
        ModelsHelper(
            binding.prompt.modelSpinner,
            binding.prompt.modelSpinnerContainer,
            viewLifecycleOwner
        ) {
            viewModel.updateModelKey(it)
        }
    }

    override fun onCreated() {
        binding.prompt.discardButton.setOnClickListener {
            when (historyItem?.status) {
                HistoryStatus.RESPONDED, HistoryStatus.ERROR -> {
                    binding.root.findNavController().navigate(
                        ResponsesFragment::class.java,
                        args = bundleOf("promptId" to historyItem!!.promptId)
                    )
                }

                else -> {
                    viewModel.discard()
                    binding.root.hideKeyboard()
                }
            }
        }
        binding.prompt.draftButton.setOnClickListener {
            viewModel.saveToDraft()
            binding.root.hideKeyboard()
        }

        binding.prompt.sendButton.setOnClickListener { view ->
            val uiVerifier: UiVerifier = security.uiVerifier()
            if (!uiVerifier.verifySendAction(view)) {
                return@setOnClickListener
            }
            viewModel.send(
                binding.prompt.chatBox.text.toString(),
                binding.prompt.schemaSpinner.selectedItem.toString()
            )
            binding.root.hideKeyboard()
        }

        binding.prompt.chatBox.addTextChangedListener {
            if (!ignoreTextUpdates) {
                viewModel.updatePrompt(it?.toString())
            }
        }

        binding.prompt.chatBox.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.prompt.sendButton.performClick()
                true
            } else {
                false
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.prompt.chatBoxContainer) { view, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.prompt.path.isVisible = !imeVisible
            binding.prompt.spinnersContainer.isVisible = !imeVisible
            binding.prompt.sendButton.isVisible = !imeVisible
            binding.prompt.draftButton.isVisible = !imeVisible
            binding.prompt.discardButton.isVisible = !imeVisible


            view.updatePaddingRelative(
                start = 0,
                top = 0,
                end = 0,
                bottom = if (imeVisible) imeInsets.bottom - systemBarInsets.bottom else 0
            )
            WindowInsetsCompat.CONSUMED
        }

        binding.prompt.chatBox.setOnTouchListener { v, event ->
            // This only matters if the parent (RecyclerView) can intercept touches.
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Explicitly request focus
                v.requestFocus()
            }
            if (v.hasFocus()) {
                v.parent.requestDisallowInterceptTouchEvent(true)
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }


        setupSchemas()
        modelsHelper.setupModels()
    }

    private fun setupSchemas() {
        viewLifecycleOwner.lifecycleScope.launch {
            val schemas = withContext(Dispatchers.IO) {
                SchemaModel.getSchemaNames()
            }
            this@SelectedHistoryViewHolder.schemas = schemas

            val adapter = ArrayAdapter(
                binding.root.context, android.R.layout.simple_spinner_item,
                schemas
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            binding.prompt.schemaSpinner.apply {
                this.adapter = adapter
                letAll(viewModel.selectedSchema, schemas) { selectedSchema, schemas ->
                    val selectedItem =
                        schemas.map { s -> s.lowercase().trim() }.indexOf(selectedSchema)
                    if (selectedItem != -1) {
                        setSelection(selectedItem)
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun bind(historyItem: HistoryItem?, position: Int) {
        this.historyItem = historyItem
        historyList.smoothScrollToPosition(position)
        bindToolbar(binding.toolbar, historyItem)
        updateChatBox(historyItem)


        val path = historyItem?.path
        val fileName = historyItem?.fileName
        binding.prompt.path.text =
            if (path?.isNotBlank() == true && fileName?.isNotBlank() == true) {
                "$path/$fileName"
            } else if (path?.isNotBlank() == true) {
                path
            } else {
                fileName
            }

        binding.prompt.discardButton.text = when (historyItem?.status) {
            HistoryStatus.RESPONDED, HistoryStatus.ERROR -> {
                "Logs"
            }

            else -> "Discard"
        }

        binding.prompt.sendButton.isVisible = historyItem?.status == HistoryStatus.PROMPTING
        binding.prompt.sendButton.text = if (historyItem?.status == HistoryStatus.RESPONDED) {
            "Re-Apply"
        } else {
            "Send"
        }
    }

    private fun updateChatBox(historyItem: HistoryItem?) {
        ignoreTextUpdates = true
        binding.prompt.chatBox.setText(historyItem?.prompt)
        ignoreTextUpdates = false
    }

    override fun onToolbarClick(historyItem: HistoryItem?) {
        viewModel.saveToDraft()
        binding.root.hideKeyboard()
    }

}