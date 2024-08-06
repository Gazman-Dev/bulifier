package com.bulifier.core.models.questions

import androidx.annotation.Keep
import com.bulifier.core.api.MessageRequest
import com.bulifier.core.models.ApiModel
import com.bulifier.core.models.QuestionsModel
import com.bulifier.core.ui.utils.Question

class ModelsQuestionsModel : QuestionsModel(
    listOf(
        Question(title = "What model to create?",
            options = listOf(
                Model.OpenAI,
                Model.Claude
            ).map {
                it.toString()
            }
        )
    )
) {
    override val modelName = "modelsModel"

    override fun createApiModel() = object : ApiModel {
        override suspend fun sendMessage(request: MessageRequest) = ""
    }

    @Keep
    enum class Model {
        ModelModels,
        OpenAI,
        Claude,
        Error
    }

    val model: Model
        get() = Model.valueOf(questions[0].response)
}