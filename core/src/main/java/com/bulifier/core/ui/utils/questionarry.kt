package com.bulifier.core.ui.utils

import android.content.Context
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bulifier.core.models.QuestionsModel

data class Question(
    val title: String,
    val options: List<String>? = null, // Null if it's a text input question
    var response: String = ""
)


fun showQuestionsDialog(questionsModel: QuestionsModel, context: Context): LiveData<Boolean> {
    val liveData: MutableLiveData<Boolean> = MutableLiveData()
    var currentIndex = 0

    fun showDialog() {
        val currentQuestion = questionsModel.questions[currentIndex]
        val dialogBuilder = AlertDialog.Builder(context)
        dialogBuilder.setTitle(currentQuestion.title + " (${currentIndex + 1}/${questionsModel.questions.size})")

        if (currentQuestion.options == null) {
            // Text input question
            val input = EditText(context)
            input.inputType = InputType.TYPE_CLASS_TEXT
            input.setText(currentQuestion.response)
            dialogBuilder.setView(input)

            dialogBuilder.setPositiveButton(if (currentIndex == questionsModel.questions.size - 1) "Submit" else "Next") { _, _ ->
                currentQuestion.response = input.text.toString()
                currentIndex++
                if (currentIndex < questionsModel.questions.size) {
                    showDialog()
                } else {
                    questionsModel.serialize()
                    liveData.value = true
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
                    currentQuestion.response = items[selected]
                    currentIndex++
                    if (currentIndex < questionsModel.questions.size) {
                        showDialog()
                    } else {
                        questionsModel.serialize()
                        liveData.value = true
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
                liveData.value = false
            }
        }

        dialogBuilder.setCancelable(true)
        dialogBuilder.setOnCancelListener {
            liveData.value = false
        }

        val dialog = dialogBuilder.create()
        dialog.show()
    }

    showDialog()
    return liveData
}
