package com.bulifier.core.models.questions

import com.bulifier.core.BuildConfig
import com.bulifier.core.models.QuestionsModel
import com.bulifier.core.models.api.AnthropicApiModel
import com.bulifier.core.ui.utils.Question

const val CLASS_GROUP_ANTHROPIC = "CLASS_GROUP_ANTHROPIC"

class AnthropicQuestionsModel() : QuestionsModel(
    CLASS_GROUP_ANTHROPIC,
    listOf(
        Question("Api Key", response = BuildConfig.CLAUDE_KEY),
        Question("Claude Model", response = "claude-3-5-sonnet-20240620", isPassword = false),
        Question("Anthropic Version", response = "2023-06-01", isPassword = false),
    )
) {

    override fun createApiModel() = AnthropicApiModel(
        apiKey = questions[0].response,
        model = questions[1].response,
        anthropicVersion = questions[2].response,
    )

    override val modelName = "claude"
}