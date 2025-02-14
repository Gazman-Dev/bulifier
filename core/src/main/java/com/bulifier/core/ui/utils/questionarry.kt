package com.bulifier.core.ui.utils

import android.content.Context
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.bulifier.core.models.QuestionsModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

data class Question(
    val title: String,
    val options: List<String>? = null, // Null if it's a text input question
    val optionsVerifiers: List<() -> Boolean>? = null, // Optionally verifiers for options
    var response: String = "",
    val isPassword: Boolean = true // When true will hide the text
)


fun showQuestionsDialog(questionsModel: QuestionsModel, context: Context): Flow<Boolean> {
    val flow = MutableStateFlow(false)
    var currentIndex = 0

    fun showDialog() {
        val currentQuestion = questionsModel.questions[currentIndex]
        val dialogBuilder = AlertDialog.Builder(context)
        dialogBuilder.setTitle(currentQuestion.title)

        if (currentQuestion.options == null) {
            // Text input question
            val input = EditText(context)
            input.inputType = InputType.TYPE_CLASS_TEXT
            input.setText(currentQuestion.response)
            if (currentQuestion.isPassword) {
                input.inputType = input.inputType or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            dialogBuilder.setView(input)

            dialogBuilder.setPositiveButton(if (currentIndex == questionsModel.questions.size - 1) "Submit" else "Next") { _, _ ->
                currentQuestion.response = input.text.toString()
                currentIndex++
                if (currentIndex < questionsModel.questions.size) {
                    showDialog()
                } else {
                    questionsModel.serialize()
                    flow.value = true
                }
            }
        } else {
            // Multiple choice question
            val items = currentQuestion.options.toTypedArray()
            var selected = currentQuestion.options.indexOf(currentQuestion.response)
            dialogBuilder.setSingleChoiceItems(items, selected) { _, which ->
                selected = which
            }

            dialogBuilder.setPositiveButton(if (currentIndex == questionsModel.questions.size - 1) "Submit" else "Next") { _, _ ->
                if (selected != -1) {
                    if (currentQuestion.optionsVerifiers?.get(selected)?.invoke() == false) {
                        flow.value = false
                    } else {
                        currentQuestion.response = items[selected]
                        currentIndex++
                        if (currentIndex < questionsModel.questions.size) {
                            showDialog()
                        } else {
                            questionsModel.serialize()
                            flow.value = true
                        }
                    }
                }
            }
        }

        if (currentIndex > 0) {
            dialogBuilder.setNegativeButton("Back") { _, _ ->
                currentIndex--
                showDialog()
            }
        } else {
            // Only for the first question
            dialogBuilder.setNegativeButton("Cancel") { _, _ ->
                flow.value = false
            }
        }

        dialogBuilder.setCancelable(true)
        dialogBuilder.setOnCancelListener {
            flow.value = false
        }

        val dialog = dialogBuilder.create()
        dialog.show()
    }

    showDialog()
    return flow
}
