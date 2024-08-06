package com.bulifier.core.models

import com.bulifier.core.prefs.Prefs
import com.bulifier.core.ui.utils.Question
import com.bulifier.core.ui.utils.createInstance
import com.bulifier.core.ui.utils.getClassName

abstract class QuestionsModel(val questions: List<Question>) {

    abstract val modelName: String
    abstract fun createApiModel(): ApiModel

    fun serialize() {
        val data = questions.joinToString(SEPARATOR) {
            it.response
        }
        Prefs.put(modelName, data)
        Prefs.put("$modelName.class", getClassName(this))
    }

    companion object {
        private const val SEPARATOR = "|,|"

        fun deserialize(modelName:String): QuestionsModel? {
            val className = Prefs.get("$modelName.class") ?: throw NullPointerException("$modelName.class not found")
            val model: QuestionsModel? = createInstance(className)

            val split = Prefs.get(modelName)?.split(SEPARATOR)
            split?.forEachIndexed { index, s ->
                model?.questions?.get(index)?.response = s
            }

            return model
        }
    }
}

