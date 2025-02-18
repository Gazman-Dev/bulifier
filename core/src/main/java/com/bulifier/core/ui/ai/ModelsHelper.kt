package com.bulifier.core.ui.ai

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bulifier.core.R
import com.bulifier.core.models.questions.ModelsQuestionsModel
import com.bulifier.core.prefs.AppSettings
import com.bulifier.core.ui.utils.showQuestionsDialog
import com.bulifier.core.utils.showToast
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ModelsHelper(
    private val modelSpinner: Spinner,
    private val modelSpinnerContainer: View,
    private val viewLifecycleOwner: LifecycleOwner,
    private val onModelSelectedListener: (modelKey: String) -> Unit
) {
    @SuppressLint("ClickableViewAccessibility")
    fun setupModels() {
        AppSettings.models.flow.value.firstOrNull()?.let {
            onModelSelectedListener(it)
        }
        updateBackground()
        viewLifecycleOwner.lifecycleScope.launch {
            val adapter = ArrayAdapter<String>(
                modelSpinner.context, android.R.layout.simple_spinner_item
            )
            val addModelKey = "Add Model"
            adapter.add(addModelKey)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            modelSpinner.adapter = adapter

            launch {
                AppSettings.models.flow.collect {
                    adapter.clear()
                    adapter.addAll(it + addModelKey)
                    updateBackground()
                }
            }

            modelSpinner.setOnTouchListener { _, event ->
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

            modelSpinner.onItemSelectedListener =
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
                            onModelSelectedListener(modelKey)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }

            updateBackground()
        }
    }

    private fun createModel() {
        if (modelSpinnerContainer.visibility != View.VISIBLE) {
            // don't show the dialog if the spinner is not visible
            return
        }
        val context = modelSpinner.context
        val scope = viewLifecycleOwner.lifecycleScope
        val questionsModel = ModelsQuestionsModel()

        showQuestionsDialog(questionsModel, context)
            .onEach { completed ->
                if (completed) {
                    val selectedModel = questionsModel.selectedModel
                    if (selectedModel == null) {
                        showToast("Invalid input")
                    } else {
                        showQuestionsDialog(selectedModel, context)
                            .onEach { selectedModelCompleted ->
                                if (selectedModelCompleted) {
                                    AppSettings.models.apply {
                                        val newModel = listOf(selectedModel.modelName)
                                        set(flow.value + newModel)
                                    }
                                    onModelSelectedListener(selectedModel.modelName)
                                }
                            }
                            .launchIn(scope) // Launch the second collection in the same scope
                    }
                }
            }
            .launchIn(scope) // Launch the first collection

    }

    private fun updateBackground() {
        modelSpinner.background = if (AppSettings.models.flow.value.isEmpty()) {
            ContextCompat.getDrawable(modelSpinner.context, R.drawable.red_background)
        } else {
            null
        }
    }
}