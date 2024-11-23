package com.bulifier.core.models.questions

import androidx.annotation.Keep
import com.bulifier.core.api.MessageRequest
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.models.ApiModel
import com.bulifier.core.models.ApiResponse
import com.bulifier.core.models.QuestionsModel
import com.bulifier.core.ui.utils.Question
import javax.inject.Inject

const val CLASS_GROUP_MODELS = "CLASS_GROUP_MODELS"
class ModelsQuestionsModel @Inject constructor() : QuestionsModel(CLASS_GROUP_MODELS,
    listOf(
        Question(title = "What model to create?",
            options = listOf(
                Model.OpenAI,
                Model.Claude
            ).map {
                it.modelName
            }
        )
    )
) {
    override val modelName = "modelsModel"

    override fun createApiModel() = object : ApiModel {
        override suspend fun sendMessage(
            request: MessageRequest,
            historyITem: HistoryItem,
            id: Long
        ) =
            ApiResponse("")
    }

    @Keep
    enum class Model(val modelName: String) {
        ModelModels("ModelModel"),
        OpenAI("OpenAI"),
        Claude("Claude"),
        Error("Error");

        companion object {
            fun fromString(modelName: String) = entries.find { it.modelName == modelName } ?: Error
        }
    }

    val selectedModel: QuestionsModel?
        get() {
            val model = Model.fromString(questions[0].response)
            return when (model) {
                Model.Claude -> AnthropicQuestionsModel()
                Model.OpenAI -> OpenAiQuestionsModel()
                else -> null
            }
        }
}