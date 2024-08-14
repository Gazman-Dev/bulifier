package com.bulifier.core.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryViewModel(val app: Application) : AndroidViewModel(app) {
    var detailedItem: LiveData<HistoryItem?> = MutableLiveData(null)

    private val historyDb by lazy { app.db.historyDao() }
    private val filesDb by lazy { app.db.fileDao() }

    val historySource by lazy {
        MediatorLiveData<PagingData<HistoryItemWithSelection>>().apply {
            addSource(projectId) { newProjectId ->
                updatePagingData(newProjectId, detailedItem.value?.promptId)
            }

            addSource(detailedItem) { detailedItem ->
                updatePagingData(projectId.value, detailedItem?.promptId)
            }
        }
    }

    private fun updatePagingData(projectId: Long?, promptId: Long?) {
        viewModelScope.launch {
            Pager(
                config = PagingConfig(pageSize = 20),
                pagingSourceFactory = {
                    historyDb.getHistory(promptId, projectId ?: -1)
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
        detailedItem.apply {
            this as MutableLiveData
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
        detailedItem.apply {
            val value = value
            viewModelScope.launch {
                value?.let {
                    historyDb.updateHistory(it)
                }
            }
            this as MutableLiveData
            this.value = null
        }
    }

    fun updateModelKey(model: String) {
        detailedItem.also { item ->
            item as MutableLiveData
            val historyItem = item.value
            if (historyItem != null) {
                item.value = historyItem.copy(
                    modelId = model
                )
                viewModelScope.launch {
                    historyDb.updateHistory(historyItem)
                }
            }
        }
    }

    fun selectDetailedItem(historyItem: HistoryItem?) {
        detailedItem.apply {
            this as MutableLiveData
            value = historyItem
        }
    }

    fun callAi(pathWithProjectName: String, fileName: String?) {
        val path = dropProjectName(pathWithProjectName)
        viewModelScope.launch {
            val schema = if (path.endsWith(".bul")) {
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
                contextFiles = filesContext
            )
            val id = historyDb.addHistory(historyItem)
            withContext(Dispatchers.Main) {
                detailedItem.apply {
                    this as MutableLiveData
                    value = historyItem.copy(promptId = id)
                }
            }
        }
    }

    private fun parseImport(importStatement: String): Pair<String, String> {
        val parts = importStatement.trim().split("/")
        val path = parts.dropLast(1).joinToString("/")
        val fileName = "${parts.last()}.bul"
        return Pair(path, fileName)
    }

    private fun extractImports(fileContent: String) = fileContent.lines()
        .map { it.trim() }
        .filter { it.startsWith("import") }
        .map { it.substringAfter("import").trim() }

    private fun dropProjectName(p: String) = p.replace((Prefs.projectName.value ?: "") + "/", "")

    fun send(prompt: String) {
        viewModelScope.launch {
            detailedItem.apply {
                this as MutableLiveData
                val value = value
                this.value = null
                value?.apply {
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

    fun updatePrompt(it: String?) {
        if (detailedItem.value?.prompt != it) {
            detailedItem.apply {
                this as MutableLiveData
                value = value?.copy(prompt = it ?: "")
            }
        }
    }
}