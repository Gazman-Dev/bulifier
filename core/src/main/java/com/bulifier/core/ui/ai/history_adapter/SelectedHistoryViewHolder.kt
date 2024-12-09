package com.bulifier.core.ui.ai.history_adapter

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bulifier.core.R
import com.bulifier.core.databinding.SelectedHistoryItemBinding
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.db.HistoryStatus
import com.bulifier.core.models.questions.ModelsQuestionsModel
import com.bulifier.core.navigation.findNavController
import com.bulifier.core.prefs.Prefs
import com.bulifier.core.schemas.SchemaModel
import com.bulifier.core.security.ProductionSecurityFactory
import com.bulifier.core.security.UiVerifier
import com.bulifier.core.ui.ai.HistoryViewModel
import com.bulifier.core.ui.ai.responses.ResponsesFragment
import com.bulifier.core.ui.utils.hideKeyboard
import com.bulifier.core.ui.utils.letAll
import com.bulifier.core.ui.utils.showQuestionsDialog
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

        ViewCompat.setOnApplyWindowInsetsListener(binding.prompt.chatBox) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.setPadding(
                systemBarInsets.left,
                systemBarInsets.top,
                systemBarInsets.right,
                imeInsets.bottom
            )
            insets
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
        setupModels()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            val adapter = ArrayAdapter<String>(
                binding.root.context, android.R.layout.simple_spinner_item
            )
            val addModelKey = "Add Model"
            adapter.add(addModelKey)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.prompt.modelSpinner.adapter = adapter

            launch {
                Prefs.models.flow.collect {
                    adapter.clear()
                    adapter.addAll(it + addModelKey)
                    updateBackground()
                }
            }

            binding.prompt.modelSpinner.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP && adapter.count == 1) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        createModel()
                    }
                    updateBackground()
                    true
                } else {
                    false
                }
            }

            binding.prompt.modelSpinner.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        if (position == adapter.count - 1) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                createModel()
                            }
                        } else {
                            val modelKey = adapter.getItem(position)!!
                            viewModel.updateModelKey(modelKey)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }

            updateBackground()
        }
    }

    private fun createModel() {
        if (binding.prompt.modelSpinnerContainer.visibility != View.VISIBLE) {
            // don't show the dialog if the spinner is not visible
            return
        }
        val context = binding.root.context
        val scope = viewLifecycleOwner.lifecycleScope
        val questionsModel = ModelsQuestionsModel()

        showQuestionsDialog(questionsModel, context)
            .onEach { completed ->
                if (completed) {
                    val selectedModel = questionsModel.selectedModel
                    if (selectedModel == null) {
                        Toast.makeText(
                            context,
                            "Invalid input",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        showQuestionsDialog(selectedModel, context)
                            .onEach { selectedModelCompleted ->
                                if (selectedModelCompleted) {
                                    Prefs.models.apply {
                                        val newModel = listOf(selectedModel.modelName)
                                        set(flow.value + newModel)
                                    }
                                    viewModel.updateModelKey(selectedModel.modelName)
                                }
                            }
                            .launchIn(scope) // Launch the second collection in the same scope
                    }
                }
            }
            .launchIn(scope) // Launch the first collection

    }

    private fun updateBackground() {
        binding.prompt.modelSpinner.background = if (Prefs.models.flow.value.isEmpty()) {
            ContextCompat.getDrawable(binding.root.context, R.drawable.red_background)
        } else {
            null
        }
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

        binding.prompt.path.text =
            "${historyItem?.path ?: ""}${if (historyItem?.fileName != null) "/${historyItem.fileName}" else ""}"

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