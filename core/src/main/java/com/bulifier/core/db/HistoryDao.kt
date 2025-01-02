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
       CASE WHEN :promptId = prompt_id THEN 1 ELSE 0 END as selected 
    FROM history 
    WHERE project_id = :projectId 
    ORDER BY last_updated DESC
"""
    )
    fun getHistory(promptId: Long, projectId: Long): PagingSource<Int, HistoryItemWithSelection>

    @Query(
        """
    SELECT * 
    FROM history 
        WHERE prompt_id = :promptId 
    ORDER BY last_updated DESC
"""
    )
    fun getHistoryItem(promptId: Long): HistoryItem

    @Query(
        """
    SELECT *, 
       CASE WHEN :promptId = prompt_id THEN 1 ELSE 0 END as selected 
    FROM history 
    WHERE project_id = :projectId 
    ORDER BY last_updated DESC
"""
    )
    suspend fun getHistoryDebug(promptId: Long, projectId: Long): List<HistoryItemWithSelection>

    @Query("SELECT prompt_id FROM history WHERE status in (:statuses) and project_id = :projectId order by last_updated")
    fun getHistoryIdsByStatuses(
        statuses: List<HistoryStatus>,
        projectId: Long
    ): Flow<List<Long>>

    @Query(
        """update history set status = 'SUBMITTED' where 
        prompt_id in (:ids) and 
        ((status = 'PROCESSING' and progress = -1) or (progress < 1 and progress != -1))
    """
    )
    suspend fun updateHistoryStatus(
        ids: List<Long>
    )

    @Query("update history set status = :newStatus where prompt_id = :id")
    suspend fun updateHistoryStatus(
        id: Long,
        newStatus: HistoryStatus
    )

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

    @Query("update history set progress = :progress where prompt_id = :promptId and progress < :progress")
    suspend fun updateProgress(promptId: Long, progress: Float)

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
            WHEN progress != -1 THEN progress * 2.0  -- Use a floating-point literal
            WHEN status = 'RESPONDED' THEN 1.0  -- Use a floating-point literal
            ELSE 0  -- Use a floating-point literal
        END) / 
    SUM(CASE 
            WHEN progress != -1 THEN 2.0  -- Use a floating-point literal
            ELSE 1.0  -- Use a floating-point literal
        END) AS progress_percentage
FROM history
WHERE status NOT IN ('ERROR', 'RESPONDED', 'PROMPTING') or (status = 'RESPONDED' and progress != -1)
  AND project_id = :projectId;

        """
    )
    fun getProgress(projectId: Long): Flow<Double?>
}