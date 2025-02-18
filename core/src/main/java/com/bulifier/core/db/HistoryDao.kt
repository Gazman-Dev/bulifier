package com.bulifier.core.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query(
        """
    SELECT *, 
       CASE WHEN :promptId = prompt_id THEN 1 ELSE 0 END as selected,
       CASE WHEN schema = 'agent' then 0 else 1 end as agent_order
    FROM history 
    WHERE project_id = :projectId 
    ORDER BY prompt_id DESC, agent_order ASC
"""
    )
    fun getHistory(promptId: Long, projectId: Long): PagingSource<Int, HistoryItemWithSelection>

    @Query(
        """
    SELECT * 
    FROM history 
        WHERE prompt_id = :promptId 
    ORDER BY prompt_id DESC
"""
    )
    fun getHistoryItem(promptId: Long): HistoryItem

    @Query(
        """
    SELECT *, 
       CASE WHEN :promptId = prompt_id THEN 1 ELSE 0 END as selected ,
       CASE WHEN schema = 'agent' then 0 else 1 end as agent_order
    FROM history 
    WHERE project_id = :projectId 
    ORDER BY prompt_id DESC
"""
    )
    suspend fun getHistoryDebug(promptId: Long, projectId: Long): List<HistoryItemWithSelection>

    @Query("SELECT prompt_id FROM history WHERE status in (:statuses) and project_id = :projectId order by prompt_id")
    fun getHistoryIdsByStatuses(
        statuses: List<HistoryStatus>,
        projectId: Long
    ): Flow<List<Long>>

    @Query("update history set status = :newStatus where prompt_id = :id")
    suspend fun updateHistoryStatus(
        id: Long,
        newStatus: HistoryStatus
    )

    @Query(
        """update history set status = 'SUBMITTED' where 
        prompt_id in (:ids) and status = 'PROCESSING'
    """
    )
    suspend fun updateHistoryStatus(ids: List<Long>)

    @Query("update history set status = 'PROCESSING' where prompt_id = :promptId and status in (:statuses)")
    suspend fun startProcessingHistoryItem(
        promptId: Long,
        statuses: List<HistoryStatus>
    ): Int

    @Delete
    suspend fun deleteHistoryItem(history: HistoryItem)

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun addHistory(history: HistoryItem): Long

    @Update
    suspend fun updateHistory(history: HistoryItem)

    @Query("update history set status = 'ERROR', error_message = :errorMessage where prompt_id = :promptId")
    suspend fun markError(promptId: Long, errorMessage: String)

    data class ErrorData(val promptId: Long, val errorMessage: String)

    @Transaction
    suspend fun markErrors(errors: List<ErrorData>) {
        errors.forEach {
            markError(it.promptId, it.errorMessage)
        }
    }

    @Query("SELECT * FROM responses WHERE prompt_id = :promptId")
    suspend fun getResponses(promptId: Long): List<ResponseItem>

    @Query("SELECT error_message FROM history WHERE prompt_id = :promptId")
    suspend fun getErrorMessages(promptId: Long): String?

    @Insert
    suspend fun addResponse(responseItem: ResponseItem): Long

    @Query(
        """
SELECT 
    SUM(CASE 
            WHEN status = 'RESPONDED' THEN 0.0
            ELSE 1.0
        END)
FROM history
WHERE status NOT IN ('ERROR', 'RESPONDED', 'PROMPTING')
  AND project_id = :projectId;

        """
    )
    fun getProgress(projectId: Long): Flow<Double?>
}