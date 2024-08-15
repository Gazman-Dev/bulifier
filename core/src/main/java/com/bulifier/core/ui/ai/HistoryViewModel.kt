package com.bulifier.core.ui.ai

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.db.HistoryItemWithSelection
import com.bulifier.core.db.HistoryStatus
import com.bulifier.core.db.db
import com.bulifier.core.prefs.Prefs
import com.bulifier.core.prefs.Prefs.projectId
import kotlinx.coroutines.launch

class HistoryViewModel(val app: Application) : AndroidViewModel(app) {
    private val detailedItem = MutableLiveData<HistoryItem?>()

    private val historyDb by lazy { app.db.historyDao() }
    private val filesDb by lazy { app.db.fileDao() }

    val selectedSchema: String?
        get() = detailedItem.value?.schema

    val historySource by lazy {
        MediatorLiveData<PagingData<HistoryItemWithSelection>>().apply {
            addSource(projectId) { newProjectId ->
                updatePagingData("projectId", detailedItem.value?.promptId, newProjectId)
            }

            addSource(detailedItem) { detailedItem ->
                updatePagingData("detailedItem", detailedItem?.promptId, projectId.value)
            }
        }
    }

    private var lastUpdate: Pair<Long?, Long?>? = null
    private fun updatePagingData(source: String, promptId: Long?, projectId: Long?) {
//        val ignoreUpdate = lastUpdate?.first == promptId && lastUpdate?.second == projectId
//        if (ignoreUpdate) {
//            return
//        }
//        lastUpdate = Pair(promptId, projectId)
        viewModelScope.launch {
            ("source=$source, promptId=$promptId, projectId=$projectId model= ${this@HistoryViewModel}\n"
                    + historyDb.getHistoryDebug(promptId ?: -1, projectId ?: -1).joinToString("\n") {
                "promptId=${it.historyItem.promptId}, projectId=${it.historyItem.projectId} selected=${it.selected}"
            }).run {
                Log.d("HistoryViewModel", "updatePagingData: $this")
            }
            Pager(
                config = PagingConfig(pageSize = 20),
                pagingSourceFactory = {
                    historyDb.getHistory(promptId ?: -1, projectId ?: -1)
                }
            ).flow.cachedIn(viewModelScope).collect {
                historySource.value = it
            }
        }
    }

    fun deleteHistoryItem(historyItem: HistoryItem) {
        viewModelScope.launch {
            historyDb.deleteHistoryItem(historyItem)
        }
    }

    suspend fun getResponses(promptId: Long) = historyDb.getResponses(promptId)

    fun discard() {
        Log.d("HistoryViewModel", "discard: ${detailedItem.value}")
        detailedItem.apply {
            val item = value
            if (item != null) {
                viewModelScope.launch {
                    historyDb.deleteHistoryItem(item)
                    value = null
                }
            }
        }
    }

    fun saveToDraft() {
        Log.d("HistoryViewModel", "saveToDraft: ${detailedItem.value}")
        detailedItem.apply {
            viewModelScope.launch {
                value?.let {
                    historyDb.updateHistory(it)
                }
                value = null
            }
        }
    }

    fun updateModelKey(model: String) {
        if(detailedItem.value?.modelId == model) {
            return
        }
        Log.d("HistoryViewModel", "updateModelKey: $model")
        detailedItem.apply {
            val historyItem = value
            if (historyItem != null) {
                value = historyItem.copy(
                    modelId = model
                )
                viewModelScope.launch {
                    historyDb.updateHistory(historyItem)
                }
            }
        }
    }

    fun selectDetailedItem(historyItem: HistoryItem?) {
        Log.d("HistoryViewModel", "selectDetailedItem: $historyItem")
        detailedItem.value = historyItem
    }

    fun createNewAiJob(pathWithProjectName: String, fileName: String?) {
        Log.d("HistoryViewModel", "createNewAiJob: $pathWithProjectName, $fileName")
        val path = dropProjectName(pathWithProjectName)
        viewModelScope.launch {
            val schema = if (fileName != null) {
                HistoryItem.SCHEMA_REBULIFY_FILE
            } else if (filesDb.isPathEmpty(path, projectId.value!!)) {
                HistoryItem.SCHEMA_BULLIFY
            } else {
                HistoryItem.SCHEMA_DEBULIFY
            }

            val filesContext = if (fileName != null) {
                filesDb.getRawContentByPath(path, fileName, projectId.value!!)?.run {
                    extractImports(this)
                } ?: emptyList()
            } else {
                emptyList()
            }.mapNotNull {
                val (importPath, importFileName) = parseImport(it)
                filesDb.getFileId(importPath, importFileName, projectId.value!!)
            }

            val historyItem = HistoryItem(
                path = path,
                fileName = fileName,
                schema = schema,
                contextFiles = filesContext,
                modelId = Prefs.models.value?.firstOrNull()
            )
            val id = historyDb.addHistory(historyItem)
            detailedItem.value = historyItem.copy(promptId = id)
        }
    }

    private fun parseImport(importStatement: String): Pair<String, String> {
        val parts = importStatement.trim().split("[^A-Za-z0-9_]".toRegex())
        val path = parts.dropLast(1).joinToString("/")
        val fileName = "${parts.last()}.bul"
        return Pair(path, fileName)
    }

    private fun extractImports(fileContent: String): List<String> {
        val lines = fileContent.lines()
        val trimmed = lines.map { it.trim() }
        val filtered = trimmed.filter { it.startsWith("import") }
        val remapped = filtered.map { it.substringAfter("import").trim() }
        return remapped
    }

    private fun dropProjectName(p: String) = p.replace((Prefs.projectName.value ?: "") + "/", "")

    fun send(prompt: String) {
        Log.d("HistoryViewModel", "send: $prompt")
        viewModelScope.launch {
            detailedItem.apply {
                val historyItem = value
                value = null
                historyItem?.apply {
                    historyDb.updateHistory(
                        copy(
                            status = HistoryStatus.SUBMITTED,
                            prompt = prompt
                        )
                    )
                }

            }
        }
    }

    fun updatePrompt(prompt: String?) {
        Log.d("HistoryViewModel", "updatePrompt: $prompt")
        detailedItem.value?.apply {
            if (this.prompt != prompt) {
                detailedItem.value = copy(prompt = prompt ?: "")
            }
        }
    }
}