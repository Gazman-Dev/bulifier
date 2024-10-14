package com.bulifier.core.ui.ai.history_adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bulifier.core.R
import com.bulifier.core.databinding.CoreSelectedHistoryItemBinding
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.db.HistoryStatus
import com.bulifier.core.models.QuestionsModel
import com.bulifier.core.models.questions.AnthropicQuestionsModel
import com.bulifier.core.models.questions.ModelsQuestionsModel
import com.bulifier.core.models.questions.OpenAiQuestionsModel
import com.bulifier.core.prefs.Prefs
import com.bulifier.core.ui.ai.AiHistoryFragmentDirections
import com.bulifier.core.ui.ai.HistoryViewModel
import com.bulifier.core.ui.utils.hideKeyboard
import com.bulifier.core.ui.utils.showQuestionsDialog
import kotlinx.coroutines.launch

class SelectedHistoryViewHolder(
    private val viewModel: HistoryViewModel,
    private val historyList: RecyclerView,
    private val binding: CoreSelectedHistoryItemBinding,
    private val schemas: Array<String>,
    private val viewLifecycleOwner: LifecycleOwner
) :
    BaseHistoryViewHolder(binding.root, viewModel) {

    private var ignoreTextUpdates = false
    private var historyItem: HistoryItem? = null

    override fun onCreated() {
        binding.prompt.discardButton.setOnClickListener {
            when (historyItem?.status) {
                HistoryStatus.RESPONDED, HistoryStatus.ERROR -> {
                    binding.root.findNavController().navigate(
                        AiHistoryFragmentDirections.toResponsesFragment(
                            historyItem!!.promptId,
                        )
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

        binding.prompt.sendButton.setOnClickListener {
            viewModel.send(binding.prompt.chatBox.text.toString(), binding.prompt.schemaSpinner.selectedItem.toString())
            binding.root.hideKeyboard()
        }

        binding.prompt.chatBox.addTextChangedListener {
            if (!ignoreTextUpdates) {
                viewModel.updatePrompt(it?.toString())
            }
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
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.prompt.modelSpinner.adapter = adapter

            val addModelKey = "Add Model"
            launch {
                Prefs.models.flow.collect {
                    adapter.clear()
                    adapter.addAll(it + addModelKey)
                    updateBackground()

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
                }
            }
            if (Prefs.models.flow.value.isEmpty()) {
                adapter.add(addModelKey)
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
                updateBackground()
            }
        }
    }

    private suspend fun createModel() {
        val context = binding.root.context

        val modelsQuestionsModel = ModelsQuestionsModel()
        showQuestionsDialog(
            modelsQuestionsModel,
            context
        ).collect { completed ->
            if (completed) {
                when (modelsQuestionsModel.model) {
                    ModelsQuestionsModel.Model.OpenAI -> showModelQuestions(
                        context,
                        OpenAiQuestionsModel()
                    )

                    ModelsQuestionsModel.Model.Claude -> showModelQuestions(
                        context,
                        AnthropicQuestionsModel()
                    )

                    ModelsQuestionsModel.Model.Error, ModelsQuestionsModel.Model.ModelModels -> Toast.makeText(
                        context,
                        "Invalid input",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun showModelQuestions(context: Context, model: QuestionsModel) {
        showQuestionsDialog(
            model,
            context
        ).collect { completed ->
            if (completed) {
                Prefs.models.apply {
                    val newModel = listOf(model.modelName)
                    set(flow.value + newModel)
                }
                viewModel.updateModelKey(model.modelName)
            }
        }
    }

    private fun updateBackground() {
        binding.prompt.modelSpinner.background = if (Prefs.models.flow.value.isEmpty()) {
            ContextCompat.getDrawable(binding.root.context, R.drawable.core_red_background)
        } else {
            null
        }
    }

    private fun setupSchemas() {
        val adapter = ArrayAdapter(
            binding.root.context, android.R.layout.simple_spinner_item,
            schemas
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.prompt.schemaSpinner.apply {
            this.adapter = adapter
            viewModel.selectedSchema?.let {
                val selectedItem = schemas.map { s -> s.lowercase().trim() }.indexOf(it)
                if (selectedItem != -1) {
                    setSelection(selectedItem)
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