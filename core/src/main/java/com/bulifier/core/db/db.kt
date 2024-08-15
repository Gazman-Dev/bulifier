@file:Suppress("FunctionName")

package com.bulifier.core.db

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.Transaction
import androidx.room.Update
import com.bulifier.core.db.Content.Type

val Context.db: AppDatabase
    get() = getDatabase(this)

private var INSTANCE: AppDatabase? = null
private fun getDatabase(context: Context): AppDatabase {
    return INSTANCE ?: synchronized("") {
        val instance = INSTANCE ?: Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "app_database"
        )
            .addTypeConverter(DateTypeConverter())
            .addTypeConverter(SetConverter())
            .addTypeConverter(LongListConverter())
//            .addMigrations(Migration(1, 2){
//                it.execSQL("ALTER TABLE history ADD COLUMN error_message TEXT DEFAULT NULL")
//            })
            .build()
        INSTANCE = instance
        instance
    }
}

data class HistoryItemWithSelection(
    @Embedded val historyItem: HistoryItem,
    val selected: Boolean
)

@Dao
interface SchemaDao {

    @Insert
    suspend fun addSchema(schema: Schema)

    @Query("DELETE FROM schemas")
    suspend fun deleteAllSchemas()

    @Transaction
    suspend fun addSchemas(schemas: List<Schema>) {
        deleteAllSchemas()
        schemas.forEach {
            addSchema(it)
        }
    }

    @Query("SELECT * FROM schemas where schema_name = :schemaName order by schema_id")
    suspend fun getSchema(schemaName: String): Array<Schema>

    @Query("SELECT distinct schema_name FROM schemas order by schema_name")
    suspend fun getSchemaNames(): Array<String>

}

@Dao
interface HistoryDao {
    @Query("""
    SELECT *, 
       CASE WHEN :promptId = prompt_id THEN 1 ELSE 0 END as selected 
    FROM history 
    WHERE project_id = :projectId 
    ORDER BY last_updated DESC
""")
    fun getHistory(promptId: Long, projectId: Long): PagingSource<Int, HistoryItemWithSelection>

    @Query("""
    SELECT *, 
       CASE WHEN :promptId = prompt_id THEN 1 ELSE 0 END as selected 
    FROM history 
    WHERE project_id = :projectId 
    ORDER BY last_updated DESC
""")
    suspend fun getHistoryDebug(promptId: Long, projectId: Long): List<HistoryItemWithSelection>

    @Query("SELECT * FROM history WHERE status = :status and project_id = :projectId order by last_updated")
    fun getHistoryByStatus(status: HistoryStatus, projectId: Long): LiveData<List<HistoryItem>>

    @Delete
    suspend fun deleteHistoryItem(history: HistoryItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addHistory(history: HistoryItem): Long

    @Update
    suspend fun updateHistory(history: HistoryItem)

    @Query("update history set status = 'ERROR', error_message = :errorMessage where prompt_id = :promptId")
    suspend fun markError(promptId: Long, errorMessage: String)

    @Update
    suspend fun updateResponse(responseItem: ResponseItem)

    @Query("SELECT * FROM responses WHERE prompt_id = :promptId")
    suspend fun getResponses(promptId: Long): List<ResponseItem>

    @Transaction
    suspend fun updateHistory(history: HistoryItem, responseItem: ResponseItem) {
        updateHistory(history)
        updateResponse(responseItem)
    }

    @Insert
    suspend fun addResponse(responseItem: ResponseItem): Long
}

@Dao
interface FileDao {

    @Query("SELECT * FROM files WHERE path = :path AND project_id = :projectId")
    fun fetchFilesByPathAndProjectId(path: String, projectId: Long): PagingSource<Int, File>

    @Query("SELECT * FROM files WHERE path = :path AND project_id = :projectId")
    fun fetchFilesListByPathAndProjectId(path: String, projectId: Long): List<File>

    @Query("SELECT contents.* FROM files join contents on files.file_id = contents.file_id WHERE project_id = :projectId and contents.type = :type")
    fun fetchFilesListByProjectIdAndType(
        projectId: Long,
        type: String = Type.RAW.toString()
    ): List<Content>

    @Query(
        """SELECT 
            files.file_name, 
            files.path, 
            contents.* 
        FROM contents 
            join files on contents.file_id = files.file_id
        WHERE project_id = :projectId"""
    )
    fun fetchFilesListByProjectId(
        projectId: Long
    ): List<FileData>

    @Query("SELECT * FROM files JOIN contents ON files.file_id = contents.file_id WHERE contents.content LIKE :content")
    fun searchFilesByContent(content: String): PagingSource<Int, File>

    @Query("SELECT * FROM files WHERE file_name LIKE :name")
    fun searchFilesByName(name: String): PagingSource<Int, File>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(content: Content): Long

    @Query("UPDATE files SET size = :size WHERE file_id = :fileId")
    suspend fun updateFileSize(fileId: Long, size: Int)

    @Transaction
    suspend fun insertContentAndUpdateFileSize(content: Content) {
        insertContent(content)
        updateFileSize(content.fileId, content.content.length)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: File): Long

    @Query("SELECT * FROM files WHERE path = :path AND file_name = :fileName AND project_id = :projectId LIMIT 1")
    suspend fun findParentFolder(path: String, fileName: String, projectId: Long): File?

    @Query("UPDATE files SET file_count = file_count + :count WHERE file_id = :parentId")
    suspend fun updateFileCount(parentId: Long, count: Int)

    @Transaction
    suspend fun insertFileAndUpdateParent(file: File): Long {
        val fileId = insertFile(file)
        if (fileId != -1L) {
            val parentFolder = findParentFolder(file.path, file.fileName, file.projectId)
                ?: throw IllegalArgumentException("Parent folder not found")
            updateFileCount(parentFolder.fileId, 1)
        }
        return fileId
    }

    @Transaction
    suspend fun deleteFileAndUpdateParent(file: File) {
        val parentFolder = findParentFolder(file.path, file.fileName, file.projectId)
            ?: throw IllegalArgumentException("Parent folder not found")
        updateFileCount(parentFolder.fileId, -1)
        deleteFile(file.fileId)
    }

    @Query("DELETE FROM files WHERE file_id = :fileId")
    suspend fun deleteFile(fileId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProject(project: Project): Long

    @Query("select * from projects order by last_updated desc")
    fun fetchProjects(): PagingSource<Int, Project>

    @Transaction
    suspend fun createProjectAndRoot(project: Project, defaultFiles: Array<File>) {
        val projectId = insertProject(project)
        defaultFiles.forEach {
            insertFile(it.copy(projectId = projectId))
        }
    }

    @Query(
        """SELECT 
            files.file_name, 
            files.path, 
            contents.* 
        FROM contents 
            join files on contents.file_id = files.file_id
        WHERE contents.file_id = :fileId
        """
    )
    suspend fun getContent(fileId: Long): FileData?

    @Query(
        """SELECT 
            files.file_name, 
            files.path, 
            contents.* 
        FROM contents 
            join files on contents.file_id = files.file_id
        WHERE files.path = :path and files.file_name = :fileName AND files.project_id = :projectId
        """
    )
    suspend fun getContent(path: String, fileName: String, projectId: Long): FileData?

    @Query(
        """SELECT
            min(contents.content) 
        FROM contents 
            join files on contents.file_id = files.file_id
        WHERE files.path = :path and files.file_name = :fileName AND files.project_id = :projectId
        """
    )
    suspend fun getRawContentByPath(path: String, fileName:String, projectId: Long): String?

    @Query(
        """SELECT
            min(file_id) 
        FROM files
        WHERE files.path = :path AND files.project_id = :projectId and files.file_name = :fileName
        """
    )
    suspend fun getFileId(path: String, fileName: String, projectId: Long): Long?

    @Query(
        """SELECT 
            files.file_name, 
            files.path, 
            contents.* 
        FROM contents 
            join files on contents.file_id = files.file_id
        WHERE contents.file_id IN (:fileIds)
        """
    )
    suspend fun getContent(fileIds: List<Long>): List<FileData>

    @Transaction
    suspend fun deleteProject(project: Project) {
        _deleteFilesByProjectId(project.projectId)
        _deleteHistoryProjectId(project.projectId)
        _deleteProject(project)
    }

    @Query("DELETE FROM files WHERE project_id = :projectId")
    suspend fun _deleteFilesByProjectId(projectId: Long)

    @Query("DELETE FROM history WHERE project_id = :projectId")
    suspend fun _deleteHistoryProjectId(projectId: Long)

    @Delete
    suspend fun _deleteProject(project: Project)

    @Query("select count(*) == 0 from files WHERE path = :path AND project_id = :projectId")
    suspend fun isPathEmpty(path: String, projectId: Long): Boolean
}

data class FileData(
    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "file_id")
    val fileId: Long,

    @ColumnInfo(name = "content")
    val content: String = "",

    @ColumnInfo(name = "type")
    val type: Type = Type.NONE
) {
    fun toContent() = Content(
        fileId = fileId,
        content = content,
        type = type
    )
}