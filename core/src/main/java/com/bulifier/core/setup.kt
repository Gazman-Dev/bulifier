package com.bulifier.core

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.bulifier.core.ai.AiDataManager
import com.bulifier.core.models.QuestionsModel
import com.bulifier.core.models.questions.AnthropicQuestionsModel
import com.bulifier.core.models.questions.CLASS_GROUP_ANTHROPIC
import com.bulifier.core.models.questions.CLASS_GROUP_DEBUG
import com.bulifier.core.models.questions.CLASS_GROUP_MODELS
import com.bulifier.core.models.questions.CLASS_GROUP_OPEN_AI
import com.bulifier.core.models.questions.DebugQuestionsModel
import com.bulifier.core.models.questions.ModelsQuestionsModel
import com.bulifier.core.models.questions.OpenAiQuestionsModel
import com.bulifier.core.prefs.Prefs
import com.bulifier.core.schemas.SchemaModel

class Setup : ContentProvider() {

    override fun onCreate(): Boolean {
        context!!.apply {
            Prefs.initialize(applicationContext)
            SchemaModel.init(applicationContext)
            AiDataManager.startListening(applicationContext)
        }
        QuestionsModel.apply {
            register(CLASS_GROUP_ANTHROPIC, AnthropicQuestionsModel::class.java)
            register(CLASS_GROUP_OPEN_AI, OpenAiQuestionsModel::class.java)
            register(CLASS_GROUP_MODELS, ModelsQuestionsModel::class.java)
            register(CLASS_GROUP_DEBUG, DebugQuestionsModel::class.java)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0
}