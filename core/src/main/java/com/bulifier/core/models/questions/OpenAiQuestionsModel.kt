package com.bulifier.core.models.questions

import com.bulifier.core.BuildConfig
import com.bulifier.core.models.api.OpenAiModel
import com.bulifier.core.models.QuestionsModel
import com.bulifier.core.ui.utils.Question

@Suppress("USELESS_ELVIS")
class OpenAiQuestionsModel : QuestionsModel(
    listOf(
        Question(title = "OpenAI Key", response = BuildConfig.OPEN_AI_KEY ?: ""),
        Question(title = "OpenAI Organization", response = BuildConfig.OPEN_AI_ORG ?: ""),
        Question(title = "OpenAI Model", response = "gpt-4o"),
    )
) {
    private val openAiKey
        get() = questions[0].response

    private val openAiOrg
        get() = questions[1].response

    override val modelName
        get() = questions[2].response

    override fun createApiModel() = OpenAiModel(openAiKey, openAiOrg, modelName)
}