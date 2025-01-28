package com.bulifier.core.db

import DbSyncHelper
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.nio.charset.StandardCharsets

@Dao
interface FileDao {

    @Transaction
    suspend fun dbToLocal(
        context: Context,
        projectId: Long,
        clearOldFiles: Boolean
    ) =
        DbSyncHelper(context).exportProject(projectId, clearOldFiles)

    @Transaction
    suspend fun localToDb(context: Context, projectId: Long) =
        DbSyncHelper(context).importProject(projectId)

    @Query("SELECT * FROM files WHERE path = :path and to_delete = 0 AND project_id = :projectId")
    fun fetchFilesByPathAndProjectId(path: String, projectId: Long): PagingSource<Int, File>

    @Query(
        """
    SELECT 
        CASE 
            WHEN path IS NULL OR path = '' THEN file_name 
            ELSE path || '/' || file_name 
        END AS full_path
    FROM files 
    WHERE 
        (CASE 
            WHEN path IS NULL OR path = '' THEN file_name 
            ELSE path || '/' || file_name 
        END) IN (:fileNames)
        AND project_id = :projectId"""
    )
    suspend fun verifyFiles(fileNames: List<String>, projectId: Long): List<String>

    @Query("SELECT * FROM files WHERE path = :path AND file_name like '%' || :extension AND project_id = :projectId")
    suspend fun fetchFilesListByPathAndProjectId(
        path: String,
        extension: String,
        projectId: Long
    ): List<File>

    @Query(
        """SELECT 
            files.file_name, 
            files.path, 
            files.is_file,
            contents.* 
        FROM contents 
            join files on contents.file_id = files.file_id
        WHERE files.path = :path and project_id = :projectId"""
    )
    fun loadFilesByPath(
        path: String,
        projectId: Long
    ): List<FileData>

    @Query(
        """SELECT 
            files.file_name, 
            files.path, 
            files.is_file,
            contents.* 
        FROM contents 
            join files on contents.file_id = files.file_id
        WHERE files.file_name like '%.bul' and to_delete = 0 and project_id = :projectId"""
    )
    fun loadBulletFiles(
        projectId: Long
    ): List<FileData>


    @Query(
        """SELECT 
            files.file_name, 
            files.path, 
            files.is_file,
            contents.* 
        FROM contents 
            join files on contents.file_id = files.file_id
        WHERE project_id = :projectId"""
    )
    fun fetchFilesListByProjectId(
        projectId: Long
    ): List<FileData>

    @Insert(onConflict = OnConflictStrategy.Companion.IGNORE)
    suspend fun insertContent(content: Content): Long

    @Transaction
    suspend fun upsertContent(content: Content) {
        val id = insertContent(content)
        if (id == -1L) {
            updateContent(content)
        }
    }

    @Update()
    suspend fun updateContent(content: Content): Int

    @Query("UPDATE files SET size = :size, hash = :hash, sync_hash = :syncHash WHERE file_id = :fileId")
    suspend fun updateFileMetaData(fileId: Long, size: Int, hash: Long, syncHash: Long)

    @Transaction
    suspend fun insertContentAndUpdateFileMetaData(content: Content, syncHash: Long = -1) {
        upsertContent(content)
        updateFileMetaData(
            content.fileId,
            content.content.length,
            calculateStringHash(content.content),
            syncHash
        )
    }

    @Transaction
    suspend fun updateContentAndFileMetaData(content: Content, syncHash: Long = -1): Int {
        val count = updateContent(content)
        updateFileMetaData(
            content.fileId,
            content.content.length,
            calculateStringHash(content.content),
            syncHash
        )
        return count
    }

    @Insert(onConflict = OnConflictStrategy.Companion.IGNORE)
    suspend fun insertFile(file: File): Long

    @Transaction
    suspend fun insertFileAndVerifyPath(file: File): Long {
        addParentFolders(file.path, file.projectId)
        return insertFile(file)
    }

    @Query("SELECT * FROM files WHERE path = :path AND file_name = :fileName AND project_id = :projectId LIMIT 1")
    suspend fun findParentFolder(path: String, fileName: String, projectId: Long): File?

    @Query("DELETE FROM files WHERE file_id = :fileId")
    suspend fun deleteFile(fileId: Long)

    @Query("DELETE FROM files WHERE file_id = :fileId or (path = :path and project_id = :projectId) or (path like :path || '/%'  and project_id = :projectId)")
    suspend fun deleteFolder(fileId: Long, path: String, projectId: Long)

    @Query("update files set to_delete = 1 WHERE file_id = :fileId")
    suspend fun markForDeleteAction(fileId: Long)

    @Query("update files set to_delete = 1 WHERE file_id = :fileId or (path = :path and project_id = :projectId) or (path like :path || '/%'  and project_id = :projectId)")
    suspend fun markForDeleteAction(fileId: Long, path: String, projectId: Long)

    @Query("DELETE FROM files WHERE to_delete = 1 and project_id = :projectId")
    suspend fun deleteFilesMarkedForDeletion(projectId: Long)

    @Insert(onConflict = OnConflictStrategy.Companion.IGNORE)
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
            files.is_file,
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
            files.is_file,
            contents.* 
        FROM contents 
            join files on contents.file_id = files.file_id
        WHERE contents.file_id = :fileId
        """
    )
    fun getContentFlow(fileId: Long): Flow<FileData?>

    @Query(
        """SELECT 
            content
        FROM contents 
        WHERE contents.file_id = :fileId
        """
    )
    fun getContentLiveData(fileId: Long): LiveData<String>?

    @Query(
        """SELECT 
            files.file_name, 
            files.path, 
            files.is_file,
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
    suspend fun getRawContentByPath(path: String, fileName: String, projectId: Long): String?

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
            * 
        FROM files
        WHERE files.path = :path AND files.project_id = :projectId and files.file_name = :fileName limit 1
        """
    )
    suspend fun getFile(path: String, fileName: String, projectId: Long): File?

    @Query(
        """SELECT
            * 
        FROM files
        WHERE files.file_id = :fileId
        """
    )
    suspend fun getFile(fileId: Long): File

    @Query(
        """SELECT
            file_id 
        FROM files
        WHERE files.path || '/' || files.file_name in (:filesNames) AND files.project_id = :projectId
        """
    )
    suspend fun getFilesIds(filesNames: List<String>, projectId: Long): List<Long>

    @Query(
        """SELECT 
            files.file_name, 
            files.path, 
            files.is_file,
            contents.* 
        FROM contents 
            join files on contents.file_id = files.file_id
        WHERE contents.file_id IN (:fileIds)
        """
    )
    suspend fun getContent(fileIds: List<Long>): List<FileData>

    @Transaction
    suspend fun deleteProject(project: Project) {
        _deleteProject(project)
    }

    @Delete
    suspend fun _deleteProject(project: Project)

    @Query("select count(*) == 0 from files WHERE path = :path AND project_id = :projectId")
    suspend fun isPathEmpty(path: String, projectId: Long): Boolean

    @Update(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun updateFile(file: File)

    @Transaction
    suspend fun updateFileName(newFile: File) {
        updateFile(newFile)
        addParentFolders(newFile.path, newFile.projectId)
    }

    @Transaction
    suspend fun updateFolderName(oldFolderFile: File, fullPath: String) {
        val newPath = fullPath.replace("[^a-zA-Z0-9_]".toRegex(), "/").trim().run {
            when {
                startsWith("/") -> {
                    fullPath.substring(1)
                }

                endsWith("/") -> {
                    fullPath.substring(0, fullPath.length - 1)
                }

                else -> this
            }
        }

        val oldFolderPath = if (oldFolderFile.path.isNotBlank()) {
            oldFolderFile.path + "/" + oldFolderFile.fileName
        } else {
            oldFolderFile.fileName
        }

        val newFileName = newPath.substringAfterLast('/')
        val newFolderPath = newPath.substringBeforeLast('/')

        Log.d(
            "updateFolderName",
            """
                id: ${oldFolderFile.fileId} 
                old Path: $oldFolderPath 
                new Path: $newFolderPath 
                new Name: $newFileName"""
                .trimIndent()
        )

        val updatedFile = oldFolderFile.copy(fileName = newFileName, path = newFolderPath)
        val oldFolderPathLength = oldFolderPath.length

        updateChildPathsByIndex(
            oldFolderPath,
            newPath,
            oldFolderPathLength,
            oldFolderFile.projectId
        )
        updateFile(updatedFile)
        addParentFolders(newFolderPath, oldFolderFile.projectId)
    }

    suspend fun addParentFolders(
        newFolderPath: String,
        projectId: Long
    ) {
        val pathParts = mutableListOf<String>()
        val parts = newFolderPath.split("/")
        parts.forEach {
            val folderName = it.trim()
            if (folderName.isEmpty()) return@forEach
            insertFile(
                File(
                    fileName = folderName,
                    projectId = projectId,
                    isFile = false,
                    path = pathParts.joinToString("/")
                )
            )
            pathParts.add(folderName)
        }
    }

    @Query(
        """
    update files
    set path = CASE 
            WHEN LENGTH(path) > :oldFolderPathLength 
            THEN :newFolderPath || SUBSTR(path, :oldFolderPathLength + 1) 
            ELSE :newFolderPath 
        END
    WHERE path LIKE :oldFolderPath || '%' 
    AND project_id = :projectId
"""
    )
    suspend fun updateChildPathsByIndex(
        oldFolderPath: String,
        newFolderPath: String,
        oldFolderPathLength: Int,
        projectId: Long
    )

    @Query("SELECT count(*) > 0 FROM files WHERE path = :path AND project_id = :projectId")
    suspend fun isPathExists(path: String, projectId: Long): Boolean

    @Query("SELECT count(*) > 0 FROM files WHERE path = :path and file_name = :fileName AND project_id = :projectId")
    suspend fun isFileExists(path: String, fileName: String, projectId: Long): Boolean

    @Query("SELECT * FROM projects WHERE project_id = :projectId")
    fun getProjectById(projectId: Long): Project

    @Query("SELECT * FROM files WHERE project_id = :projectId")
    fun getFilesByProjectId(projectId: Long): List<File>

    @Query(
        """
        SELECT 
            files.file_id, 
            files.file_name, 
            files.path, 
            files.is_file, 
            contents.*
        FROM files
            JOIN contents ON files.file_id = contents.file_id
        WHERE files.project_id = :projectId and files.to_delete = 0
        ORDER BY files.file_id ASC
    """
    )
    suspend fun exportFilesAndContents(projectId: Long): List<FileData>

    @Query("DELETE FROM files WHERE project_id = :projectId")
    suspend fun deleteFilesByProjectId(projectId: Long)

    @Query(
        """SELECT count(*) == 0 FROM files 
        WHERE project_id = :projectId and 
            path != 'schemas' and
            (file_name != 'schemas' or path != '')
            """
    )
    suspend fun isProjectEmpty(projectId: Long): Boolean

    @Query("SELECT * FROM projects WHERE project_id = :projectId")
    suspend fun getProject(projectId: Long): Project

    @Query("SELECT * FROM projects WHERE project_name = :projectName")
    suspend fun getProject(projectName: String): Project?

    @Update
    suspend fun updateProject(project: Project)

    @Query("SELECT count(*) > 0 FROM projects WHERE project_name = :projectName")
    suspend fun isProjectExists(projectName: String): Boolean

    private fun calculateStringHash(input: String): Long {
        checksum.update(input.toByteArray(StandardCharsets.UTF_8))
        return checksum.value
    }
}