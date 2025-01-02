package com.bulifier.core.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.bulifier.core.db.HistoryItem.Companion.SCHEMA_DEBULIFY_FILE
import com.bulifier.core.db.HistoryItem.Companion.SCHEMA_UPDATE_BULLET_WITH_RAW
import com.bulifier.core.db.HistoryItem.Companion.SCHEMA_UPDATE_RAW_WITH_BULLETS
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("""
    SELECT 
        *
    FROM sync_files
    WHERE 
        project_id = :projectId AND schema = :schema
""")
    suspend fun getFilesToSync(projectId: Long, schema: String): List<SyncFile>

    @Query("DELETE FROM sync_files WHERE project_id = :projectId")
    suspend fun clearSyncFiles(projectId: Long)

    @Query(
        """
        INSERT INTO sync_files (
            file_Id,
            raw_file_id, 
            bullets_file_id, 
            schema, 
            project_id,
            last_updated
        )
        SELECT 
            bulletFile.file_id,
            rawFile.file_id,
            bulletFile.file_id,
            :schema AS schema,
            bulletFile.project_id,
            current_timestamp
        FROM files bulletFile
        JOIN files rawFile ON bulletFile.path = rawFile.path
                           AND bulletFile.file_name = rawFile.file_name || '.bul'
                           and bulletFile.project_id == rawFile.project_id
        WHERE bulletFile.project_id = :projectId
          AND bulletFile.file_name LIKE '%.bul'
          AND (:forceSync OR bulletFile.sync_hash != rawFile.hash)
    """
    )
    suspend fun insertBulletsToUpdate(
        projectId: Long,
        forceSync: Boolean,
        schema:String = SCHEMA_UPDATE_BULLET_WITH_RAW
    )

    @Query(
        """
        INSERT INTO sync_files (
            file_Id,
            raw_file_id, 
            bullets_file_id, 
            schema, 
            project_id,
            last_updated
        )
        SELECT 
            null,
            null,
            bulletFile.file_id,
            :schema AS schema,
            bulletFile.project_id,
            current_timestamp
        FROM files bulletFile
        left JOIN files rawFile ON bulletFile.path = rawFile.path
                           AND bulletFile.file_name = rawFile.file_name || '.bul'
                           and bulletFile.project_id == rawFile.project_id
        WHERE bulletFile.project_id = :projectId
          AND bulletFile.file_name LIKE '%.bul'
          and rawFile.file_id is null
    """
    )
    suspend fun insertRawsToCreate(
        projectId: Long,
        schema:String = SCHEMA_DEBULIFY_FILE
    )

    @Query(
        """
        INSERT INTO sync_files (
            file_id,
            raw_file_id, 
            bullets_file_id, 
            schema, 
            project_id,
            last_updated
        )
        SELECT
            rawFile.file_id,
            rawFile.file_id,
            bulletFile.file_id,
            :schema AS schema,
            bulletFile.project_id,
            current_timestamp
        FROM files bulletFile
        JOIN files rawFile ON bulletFile.path = rawFile.path
                           AND bulletFile.file_name = rawFile.file_name || '.bul'
                           and bulletFile.project_id == rawFile.project_id
        WHERE bulletFile.project_id = :projectId
          AND bulletFile.file_name LIKE '%.bul'
          AND (:forceSync OR rawFile.sync_hash != bulletFile.hash)
    """
    )
    suspend fun insertRawsToUpdate(
        projectId: Long,
        forceSync: Boolean,
        schema:String = SCHEMA_UPDATE_RAW_WITH_BULLETS
    )

    @Transaction
    suspend fun sync(
        projectId: Long,
        mode: SyncMode = SyncMode.AUTO,
        forceSync: Boolean = false
    ): SyncLog {
        clearSyncFiles(projectId)

        var bulletsToUpdate = 0
        var rawsToUpdate = 0
        var rawsToCreate = 0

        if (mode == SyncMode.BULLET || mode == SyncMode.AUTO) {
            insertBulletsToUpdate(projectId, forceSync)
            bulletsToUpdate = getChanges()
        }

        if (mode == SyncMode.RAW || mode == SyncMode.AUTO) {
            insertRawsToCreate(projectId)
            rawsToCreate = getChanges()

            insertRawsToUpdate(projectId, forceSync)
            rawsToUpdate = getChanges()
        }

        return (SyncLog(
            bulletsToUpdate = bulletsToUpdate,
            rawsToUpdate = rawsToUpdate,
            rawsToCreate = rawsToCreate
        ))
    }

    data class SyncLog(
        val bulletsToUpdate:Int,
        val rawsToUpdate:Int,
        val rawsToCreate:Int
    )

    // SQLite's built-in function to see how many rows were changed
    @Suppress("SpellCheckingInspection")
    @Query("SELECT changes()")
    fun getChanges(): Int
}