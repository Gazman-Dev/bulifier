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
import com.bulifier.core.db.SyncMode
import com.bulifier.core.db.db
import com.bulifier.core.prefs.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class HistoryViewModel(val app: Application) : AndroidViewModel(app) {
    private val detailedItem = MutableStateFlow<HistoryItem?>(null)

    private val db by lazy { app.db }

    private var pagingJob: Job? = null

    val selectedSchema: String?
        get() = detailedItem.value?.schema

    @OptIn(ExperimentalCoroutinesApi::class)
    val progress by lazy {
        AppSettings.project.flatMapLatest {
            db.historyDao().getProgress(it.projectId)
        }
    }

    private val _historySource =
        MutableStateFlow<PagingData<HistoryItemWithSelection>>(PagingData.empty())
    val historySource: StateFlow<PagingData<HistoryItemWithSelection>> = _historySource

    init {
        // Combine projectId and detailedItem flows and update the paging data when either changes
        viewModelScope.launch {
            combine(AppSettings.project, detailedItem) { newProject, detailedItem ->
                Pair(newProject.projectId, detailedItem?.promptId)
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
                        this@HistoryViewModel.db.historyDao()
                            .getHistoryDebug(promptId ?: -1, projectId)
                            .joinToString("\n") {
                                "promptId=${it.historyItem.promptId}, projectId=${it.historyItem.projectId} selected=${it.selected}"
                            }
            )

            Pager(
                config = PagingConfig(pageSize = 20),
                pagingSourceFactory = {
                    this@HistoryViewModel.db.historyDao().getHistory(promptId ?: -1, projectId)
                }
            ).flow.cachedIn(viewModelScope).collect {
                _historySource.value = it
            }
        }
    }

    fun deleteHistoryItem(historyItem: HistoryItem) {
        viewModelScope.launch {
            this@HistoryViewModel.db.historyDao().deleteHistoryItem(historyItem)
        }
    }

    suspend fun getResponses(promptId: Long) = db.historyDao().getResponses(promptId)
    suspend fun getErrorMessages(promptId: Long) = db.historyDao().getErrorMessages(promptId)

    fun discard() {
        Log.d("HistoryViewModel", "discard: ${detailedItem.value}")
        viewModelScope.launch {
            detailedItem.value?.let {
                this@HistoryViewModel.db.historyDao().deleteHistoryItem(it)
                detailedItem.value = null
            }
        }
    }

    fun saveToDraft() {
        Log.d("HistoryViewModel", "saveToDraft: ${detailedItem.value}")
        viewModelScope.launch {
            detailedItem.value?.let {
                this@HistoryViewModel.db.historyDao().updateHistory(it)
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
                this@HistoryViewModel.db.historyDao()
                    .updateHistory(historyItem.copy(modelId = model))
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
            val filesContext = fileName?.let {
                this@HistoryViewModel.db.fileDao()
                    .getRawContentByPath(path, it, AppSettings.project.value.projectId)?.run {
                        extractImports(this)
                    } ?: emptyList()
            }?.mapNotNull {
                val (importPath, importFileName) = parseImport(it)
                this@HistoryViewModel.db.fileDao()
                    .getFileId(importPath, importFileName, AppSettings.project.value.projectId)
            } ?: emptyList()

            val historyItem = HistoryItem(
                path = path,
                fileName = fileName,
                schema = HistoryItem.SCHEMA_AGENT,
                contextFiles = filesContext,
                modelId = AppSettings.models.flow.value.firstOrNull()
            )
            val id = this@HistoryViewModel.db.historyDao().addHistory(historyItem)
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

    private fun dropProjectName(p: String) = if (p.contains("/")) {
        p.replace("${AppSettings.project.value.projectName}/", "")
    } else {
        ""
    }

    fun send(prompt: String, schema: String) {
        Log.d("HistoryViewModel", "send: $prompt")
        viewModelScope.launch {
            detailedItem.value?.let { historyItem ->
                detailedItem.value = null
                this@HistoryViewModel.db.historyDao().updateHistory(
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

    fun addAgentMessage(
        message: String,
        modelId: String,
        pathWithProjectName: String,
        fileName: String?,
        isNativeCode: Boolean
    ) {
        val path = dropProjectName(pathWithProjectName)
        viewModelScope.launch {
            this@HistoryViewModel.db.historyDao().addHistory(
                HistoryItem(
                    modelId = modelId,
                    path = path,
                    fileName = fileName,
                    schema = HistoryItem.SCHEMA_AGENT,
                    prompt = message,
                    projectId = AppSettings.project.value.projectId,
                    status = HistoryStatus.SUBMITTED,
                    isNativeCode = isNativeCode
                )
            )
        }
    }

    suspend fun sync(
        mode: SyncMode = SyncMode.AUTO,
        modelId: String,
        forceSync: Boolean = false
    ): Boolean {
        val fileIds = db.fileDao().getFilesToSync(AppSettings.project.value.projectId, forceSync)
        if (fileIds.isEmpty()) {
            return false
        }

        this@HistoryViewModel.db.historyDao().addHistory(
            HistoryItem(
                syncBulletFiles = fileIds.map {
                    it.bulletsFile
                },
                syncRawFiles = fileIds.map {
                    it.rawFile
                },
                modelId = modelId,
                path = "",
                fileName = "",
                schema = HistoryItem.SCHEMA_DEBULIFY,
                prompt = "",
                projectId = AppSettings.project.value.projectId,
                status = HistoryStatus.SUBMITTED
            )
        )
        return true
    }


}
