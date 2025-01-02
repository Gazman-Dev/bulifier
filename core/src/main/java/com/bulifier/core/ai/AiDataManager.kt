package com.bulifier.core.ai

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bulifier.core.db.HistoryStatus
import com.bulifier.core.db.db
import com.bulifier.core.prefs.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

const val KEY_HISTORY_ITEM_ID = "historyItemId"

object AiDataManager {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun startListening(context:Context) {
        val historyFlow = Prefs.projectId.flow
            .flatMapLatest { projectId ->
                context.db.historyDao().getHistoryIdsByStatuses(
                    statuses = listOf(
                        HistoryStatus.SUBMITTED,
                        HistoryStatus.RE_APPLYING
                    ),
                    projectId = projectId
                )
            }

        scope.launch {
            historyFlow.collect { ids ->
                ids.forEach { promptId ->
                    submitJob(context, promptId)
                }
            }
        }
    }

    private fun submitJob(context: Context, promptId: Long) {
        val data = workDataOf(KEY_HISTORY_ITEM_ID to promptId)

        val request = OneTimeWorkRequestBuilder<AiWorker>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

}