package com.bulifier.core.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.liveData
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.db.HistoryStatus
import com.bulifier.core.db.db
import com.bulifier.core.prefs.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryViewModel(val app: Application) : AndroidViewModel(app) {
    var detailedItem: LiveData<HistoryItem?> = MutableLiveData(null)

    private val db by lazy { app.db.historyDao() }

    val historySource = detailedItem.switchMap {
        Pager(
            config = PagingConfig(
                pageSize = 20, // Number of items in one page
                enablePlaceholders = true,
                maxSize = 100 // Maximum number of items in the RecyclerView before pages start being dropped
            ),
            pagingSourceFactory = { db.getHistory(it?.promptId) }  // db call now depends on detailItem.id
        ).liveData.cachedIn(viewModelScope)
    }

    fun deleteHistoryItem(historyItem: HistoryItem) {
        viewModelScope.launch {
            db.deleteHistoryItem(historyItem)
        }
    }

    suspend fun getResponses(promptId: Long) = db.getResponses(promptId)

    fun discard() {
        detailedItem.apply {
            this as MutableLiveData
            val item = value
            if (item != null) {
                viewModelScope.launch {
                    db.deleteHistoryItem(item)
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
                    db.updateHistory(it)
                }
            }
            this as MutableLiveData
            this.value = null
        }
    }

    fun updateModelKey(model:String){
        detailedItem.also { item ->
            item as MutableLiveData
            val historyItem = item.value
            if(historyItem != null) {
                item.value = historyItem.copy(
                    modelId = model
                )
                viewModelScope.launch {
                    db.updateHistory(historyItem)
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

    fun bulifyPath(p:String) {
        val path = dropProjectName(p)
        viewModelScope.launch {
            val historyItem = HistoryItem(path = path)
            val id = db.addHistory(historyItem)
            withContext(Dispatchers.Main) {
                detailedItem.apply {
                    this as MutableLiveData
                    value = historyItem.copy(promptId = id)
                }
            }
        }
    }

    fun debulifyPath(p:String) {
        val path = dropProjectName(p)
        viewModelScope.launch {
            val historyItem = HistoryItem(path = path, schema = "debulify")
            val id = db.addHistory(historyItem)
            withContext(Dispatchers.Main) {
                detailedItem.apply {
                    this as MutableLiveData
                    value = historyItem.copy(promptId = id)
                }
            }
        }
    }

    private fun dropProjectName(p: String) = p.replace((Prefs.projectName.value ?: "") + "/", "")

    fun send(prompt: String) {
        viewModelScope.launch {
            detailedItem.apply {
                this as MutableLiveData
                val value = value
                this.value = null
                value?.apply {
                    db.updateHistory(
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
        if(detailedItem.value?.prompt != it) {
            detailedItem.apply {
                this as MutableLiveData
                value = value?.copy(prompt = it ?: "")
            }
        }
    }
}