package com.bulifier.core.ui.ai

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HistoryViewModel(val app: Application) : AndroidViewModel(app) {
    private val detailedItem = MutableStateFlow<HistoryItem?>(null)

    private val historyDb by lazy { app.db.historyDao() }
    private val filesDb by lazy { app.db.fileDao() }

    private var pagingJob: Job? = null

    val selectedSchema: String?
        get() = detailedItem.value?.schema

    private val _historySource =
        MutableStateFlow<PagingData<HistoryItemWithSelection>>(PagingData.empty())
    val historySource: StateFlow<PagingData<HistoryItemWithSelection>> = _historySource

    init {
        // Combine projectId and detailedItem flows and update the paging data when either changes
        viewModelScope.launch {
            combine(projectId.flow, detailedItem) { newProjectId, detailedItem ->
                Pair(newProjectId, detailedItem?.promptId)
            }.collect { (newProjectId, promptId) ->
                updatePagingData(newProjectId, promptId)
            }
        }
    }

    private fun updatePagingData(projectId: Long, promptId: Long?) {
        pagingJob?.cancel()
        pagingJob = viewModelScope.launch {
            Log.d(
                "HistoryViewModel",
                "promptId=$promptId, projectId=$projectId model= ${this@HistoryViewModel}\n" +
                        historyDb.getHistoryDebug(promptId ?: -1, projectId)
                            .joinToString("\n") {
                                "promptId=${it.historyItem.promptId}, projectId=${it.historyItem.projectId} selected=${it.selected}"
                            }
            )

            Pager(
                config = PagingConfig(pageSize = 20),
                pagingSourceFactory = {
                    historyDb.getHistory(promptId ?: -1, projectId)
                }
            ).flow.cachedIn(viewModelScope).collect {
                _historySource.value = it
            }
        }
    }

    fun deleteHistoryItem(historyItem: HistoryItem) {
        viewModelScope.launch {
            historyDb.deleteHistoryItem(historyItem)
        }
    }

    suspend fun getResponses(promptId: Long) = historyDb.getResponses(promptId)
    suspend fun getErrorMessages(promptId: Long) = historyDb.getErrorMessages(promptId)

    fun discard() {
        Log.d("HistoryViewModel", "discard: ${detailedItem.value}")
        viewModelScope.launch {
            detailedItem.value?.let {
                historyDb.deleteHistoryItem(it)
                detailedItem.value = null
            }
        }
    }

    fun saveToDraft() {
        Log.d("HistoryViewModel", "saveToDraft: ${detailedItem.value}")
        viewModelScope.launch {
            detailedItem.value?.let {
                historyDb.updateHistory(it)
            }
            detailedItem.emit(null)
        }
    }

    fun updateModelKey(model: String) {
        if (detailedItem.value?.modelId == model) return
        Log.d("HistoryViewModel", "updateModelKey: $model")
        detailedItem.value?.let { historyItem ->
            detailedItem.value = historyItem.copy(modelId = model)
            viewModelScope.launch {
                historyDb.updateHistory(historyItem.copy(modelId = model))
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
            val schema = when {
                fileName != null -> HistoryItem.SCHEMA_REBULIFY_FILE
                path == "schemas" -> HistoryItem.UPDATE_SCHEMA
                filesDb.isPathEmpty(path, projectId.flow.value) -> HistoryItem.SCHEMA_BULLIFY
                else -> HistoryItem.SCHEMA_DEBULIFY
            }

            val filesContext = fileName?.let {
                filesDb.getRawContentByPath(path, fileName, projectId.flow.value)?.run {
                    extractImports(this)
                } ?: emptyList()
            }?.mapNotNull {
                val (importPath, importFileName) = parseImport(it)
                filesDb.getFileId(importPath, importFileName, projectId.flow.value)
            } ?: emptyList()

            val historyItem = HistoryItem(
                path = path,
                fileName = fileName,
                schema = schema,
                contextFiles = filesContext,
                modelId = Prefs.models.flow.value.firstOrNull()
            )
            val id = historyDb.addHistory(historyItem)
            Log.d(
                "HistoryViewModel",
                "createNewAiJob: id: $id path: $pathWithProjectName, $fileName"
            )
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
        return fileContent.lines()
            .map { it.trim() }
            .filter { it.startsWith("import") }
            .map { it.substringAfter("import").trim() }
    }

    private fun dropProjectName(p: String) = p.replace("${Prefs.projectName.flow.value}/", "")

    fun send(prompt: String, schema: String) {
        Log.d("HistoryViewModel", "send: $prompt")
        viewModelScope.launch {
            detailedItem.value?.let { historyItem ->
                detailedItem.value = null
                historyDb.updateHistory(
                    historyItem.copy(
                        status = if (historyItem.status == HistoryStatus.RESPONDED) {
                            HistoryStatus.RE_APPLYING
                        } else {
                            HistoryStatus.SUBMITTED
                        },
                        prompt = prompt,
                        schema = schema
                    )
                )
            }
        }
    }

    fun updatePrompt(prompt: String?) {
        Log.d("HistoryViewModel", "updatePrompt: $prompt")
        detailedItem.value?.let {
            if (it.prompt != prompt) {
                detailedItem.value = it.copy(prompt = prompt ?: "")
            }
        }
    }
}
