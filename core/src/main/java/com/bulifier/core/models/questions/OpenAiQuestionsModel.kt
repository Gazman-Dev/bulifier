package com.bulifier.core.models.questions

import com.bulifier.core.BuildConfig
import com.bulifier.core.models.QuestionsModel
import com.bulifier.core.models.api.OpenAiApiModel
import com.bulifier.core.ui.utils.Question

const val CLASS_GROUP_OPEN_AI = "CLASS_GROUP_OPEN_AI"

@Suppress("USELESS_ELVIS")
class OpenAiQuestionsModel : QuestionsModel(
    CLASS_GROUP_OPEN_AI,
    listOf(
        Question(title = "OpenAI Key", response = BuildConfig.OPEN_AI_KEY ?: ""),
        Question(title = "OpenAI Model", response = "gpt-4o", isPassword = false),
    )
) {
    private val openAiKey
        get() = questions[0].response

    override val modelName
        get() = questions[1].response

    override fun createApiModel() = OpenAiApiModel(openAiKey, modelName)
}