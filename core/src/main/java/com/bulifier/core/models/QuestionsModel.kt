package com.bulifier.core.models

import com.bulifier.core.prefs.Prefs
import com.bulifier.core.ui.utils.Question

abstract class QuestionsModel(private val classGroup:String, val questions: List<Question>) {

    abstract val modelName: String
    abstract fun createApiModel(): ApiModel

    fun serialize() {
        val data = questions.joinToString(SEPARATOR) {
            it.response
        }
        Prefs.put("questions $modelName", data)
        Prefs.put("class_group $modelName", classGroup)
    }

    companion object {
        private const val SEPARATOR = "|,|"
        private val modelClasses = mutableMapOf<String, Class<out QuestionsModel>>()

        fun deserialize(modelName: String): QuestionsModel {
            val model: QuestionsModel = createModel(modelName)

            val data = Prefs.get("questions $modelName")
                ?: throw Error("No questions found for $modelName")
            data.split(SEPARATOR).forEachIndexed { index, s ->
                if (index < (model.questions.size)) {
                    model.questions[index].response = s
                }
            }

            return model
        }

        private fun createModel(modelName: String): QuestionsModel {
            val classGroup = Prefs.get("class_group $modelName")
                ?: throw Error("No class group found for $modelName")

            val klass = modelClasses[classGroup] ?: throw Error("Model $classGroup class not found")
            val constructor = klass.declaredConstructors.firstOrNull {
                it.parameterCount == 0
            } ?: throw Error("No suitable constructor found for $classGroup")

            constructor.isAccessible = true
            val model: QuestionsModel = constructor.newInstance() as QuestionsModel
            return model
        }

        fun register(classGroup: String, klass: Class<out QuestionsModel>) {
            modelClasses[classGroup] = klass
        }
    }
}

