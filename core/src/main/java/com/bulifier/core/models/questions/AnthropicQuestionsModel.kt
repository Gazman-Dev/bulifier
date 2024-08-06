package com.bulifier.core.models.questions

import com.bulifier.core.BuildConfig
import com.bulifier.core.models.api.AnthropicModel
import com.bulifier.core.models.QuestionsModel
import com.bulifier.core.ui.utils.Question

class AnthropicQuestionsModel : QuestionsModel(
    listOf(
        Question("Api Key", response = BuildConfig.CLAUDE_KEY)
    )
){
    private val openAiKey
        get() = questions[0].response

    override fun createApiModel() = AnthropicModel(openAiKey)

    override val modelName = "claude"
}