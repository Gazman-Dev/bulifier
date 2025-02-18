package com.bulifier.core.db

import DbSyncHelper
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bulifier.core.utils.Logger
import isTextAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.io.outputStream

@Dao
interface FileDao {

    @Transaction
    suspend fun dbToLocal(
        context: Context,
        projectId: Long,
        clearOldFiles: Boolean
    ) = DbSyncHelper(context).exportProject(projectId, clearOldFiles)

    @Transaction
    suspend fun dbToFolder(
        context: Context,
        projectId: Long,
        destination: java.io.File,
        clearOldFiles: Boolean,
        extensionsBlackList: List<String>,
    ) = DbSyncHelper(context).exportProject(
        projectId,
        destination,
        clearOldFiles,
        extensionsBlackList
    )

    @Transaction
    suspend fun localToDb(context: Context, projectId: Long) =
        DbSyncHelper(context).importProject(projectId)

    @Query("SELECT * FROM files WHERE path = :path and not to_delete AND project_id = :projectId order by is_file, file_name ")
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

    @Query("UPDATE files SET size = :size, hash = :hash WHERE file_id = :fileId")
    suspend fun updateFileMetaData(fileId: Long, size: Long, hash: Long)

    @Transaction
    suspend fun insertContentAndUpdateFileMetaData(content: Content) {
        upsertContent(content)
        updateFileMetaData(
            content.fileId,
            content.content.length.toLong(),
            calculateStringHash(content.content)
        )
    }

    @Transaction
    suspend fun updateContentAndFileMetaData(content: Content): Int {
        val count = updateContent(content)
        updateFileMetaData(
            content.fileId,
            content.content.length.toLong(),
            calculateStringHash(content.content)
        )
        return count
    }

    @Transaction
    suspend fun insertFile(file: File): Long {
        Log.d("insertFile", "file: $file")
        return _insertFile(file)
    }

    @Insert(onConflict = OnConflictStrategy.Companion.IGNORE)
    suspend fun _insertFile(file: File): Long

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

    @Transaction
    suspend fun createProject(context: Context, project: Project, logger: Logger): Long {
        val projectId = insertProject(project)
        if (project.template == null) {
            return projectId
        }

        val templatePath = "templates/${project.template}".trim()
        loadTemplateFiles(
            context = context,
            projectId = projectId,
            templatePath = templatePath,
            logger = logger
        )
        return projectId
    }

    private suspend fun loadTemplateFiles(
        context: Context,
        projectId: Long,
        templatePath: String,
        parentPath: String = "",
        logger: Logger
    ): Unit = withContext(Dispatchers.IO) {
        val assetFiles = try {
            context.assets.list(templatePath) ?: return@withContext
        } catch (e: Exception) {
            logger.e("error loadTemplateFiles", e)
            return@withContext
        }

        for (file in assetFiles) {
            val isFile = file.contains(".")
            val fullPath = "$templatePath/$file"

            val isBinary = isFile && !context.assets.isTextAsset(fullPath)

            val fileId = insertFile(
                File(
                    fileName = file,
                    projectId = projectId,
                    isFile = isFile,
                    path = parentPath,
                    isBinary = isBinary
                )
            )

            if (isFile) {
                if (isBinary) {
                    val destination: java.io.File = getBinaryFile(context, fileId, projectId)
                    val inputStream = context.assets.open(fullPath)
                    val size = copyStreamToFile(inputStream, destination)
                    updateFileMetaData(fileId, size, -1L)
                } else {
                    val content = context.assets.open(fullPath).bufferedReader()
                        .use { it.readText() }
                    insertContentAndUpdateFileMetaData(
                        Content(fileId = fileId, content = content)
                    )
                }
            } else {
                loadTemplateFiles(
                    context,
                    projectId,
                    "$templatePath/$file",
                    if (parentPath.isEmpty()) file else "$parentPath/$file",
                    logger
                )
            }
        }

    }

    private fun copyStreamToFile(inputStream: InputStream, destination: java.io.File): Long {
        // Ensure the parent directories exist
        destination.parentFile?.mkdirs()

        // Delete the destination file if it exists (overwrite behavior)
        if (destination.exists()) {
            destination.delete()
        }

        // Use Kotlin's extension functions to auto-close streams and copy data.
        return inputStream.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)  // returns the total number of bytes copied
            }
        }
    }


    @Query("select * from projects order by last_updated desc")
    fun fetchProjects(): PagingSource<Int, Project>

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
        WHERE (case when files.path is null or files.path = '' 
                then files.file_name 
                else files.path || '/' || files.file_name end) 
            in (:filesNames) AND files.project_id = :projectId
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
    WHERE files.project_id = :projectId 
        AND files.to_delete = 0
        AND (
            LOWER(SUBSTR(files.file_name, INSTR(files.file_name, '.') + 1)) NOT IN (:blacklist)
            OR INSTR(files.file_name, '.') = 0  -- Allow files without extensions
        )
    ORDER BY files.file_id ASC
    """
    )
    suspend fun exportFilesAndContents(projectId: Long, blacklist: List<String>): List<FileData>


    @Query(
        """
        SELECT 
            *
        FROM files
        WHERE project_id = :projectId and is_binary
        ORDER BY files.file_id ASC
    """
    )
    suspend fun exportBinaryFiles(projectId: Long): List<File>

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
    suspend fun getProject(projectId: Long): Project?

    @Query("SELECT * FROM projects WHERE project_name = :projectName")
    suspend fun getProject(projectName: String): Project?

    @Update
    suspend fun updateProject(project: Project)

    @Query("SELECT count(*) > 0 FROM projects WHERE project_name = :projectName")
    suspend fun isProjectExists(projectName: String): Boolean

    @Query("SELECT project_name FROM projects")
    suspend fun projectNames(): List<String>

    private fun calculateStringHash(input: String): Long {
        checksum.update(input.toByteArray(StandardCharsets.UTF_8))
        return checksum.value
    }

    @Transaction
    suspend fun transaction(function: suspend () -> Unit) {
        function()
    }


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDependency(dependencies: List<Dependency>)

    @Transaction
    suspend fun insertDependencies(fileId: Long, dependencies: List<Long>, projectId: Long) {
        val dependencyEntities = dependencies.map { dependencyFileId ->
            Dependency(
                fileId = fileId,
                dependencyFileId = dependencyFileId,
                projectId = projectId
            )
        }
        insertDependency(dependencyEntities)
    }

    @Query(
        """
        SELECT files.* FROM dependencies 
            join files on dependency_file_id = files.file_id
        WHERE dependencies.file_id = :fileId and dependencies.project_id = :projectId
    """
    )
    suspend fun getDependencies(fileId: Long, projectId: Long): List<File>

    @Query(
        """
        SELECT dependency_file_id FROM dependencies
        WHERE file_id in (:fileIds) and project_id = :projectId
    """
    )
    fun getDependenciesIds(fileIds: List<Long>, projectId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.Companion.IGNORE)
    suspend fun addImport(dependency: Dependency)

    @Query("delete from dependencies where file_id = :fileId and dependency_file_id = :dependencyFileId and project_id = :projectId")
    suspend fun removeImport(fileId: Long, dependencyFileId: Long, projectId: Long)

    @Query(
        """
        SELECT files.* FROM dependencies 
            join files on dependency_file_id = files.file_id
        WHERE dependencies.file_id = :fileId and dependencies.project_id = :projectId
        order by path, file_name
    """
    )
    suspend fun getImports(fileId: Long, projectId: Long): List<File>

    @Query(
        """
        SELECT * FROM files
        WHERE project_id = :projectId and file_name like '%.bul'
        and file_id != :fileId and file_id not in (
            select dependency_file_id from dependencies 
            where file_id = :fileId and project_id = :projectId        
        )
        order by path, file_name
    """
    )
    suspend fun getAllImports(fileId: Long, projectId: Long): List<File>

    @Query(
        """
        select 
            bullets.file_id bullet_file_id, 
            case when raw.file_id is null then -1 else raw.file_id end raw_file_id
        from files bullets
        left join files raw on 
            bullets.file_name = raw.file_name || ".bul" and 
            bullets.path = raw.path and
            bullets.project_id = raw.project_id
        where (bullets.sync_hash != bullets.hash or bullets.sync_hash == -1 or :forceSync = 1)
        and bullets.file_name like '%.bul' and bullets.project_id = :projectId
    """
    )
    suspend fun getFilesToSync(projectId: Long, forceSync: Boolean): List<SyncFile>

    @Query("update files set sync_hash = hash where file_id in (:fileIds)")
    fun markSynced(fileIds: List<Long>)

    @Query("select * from files where file_id in (:fileIds)")
    fun getFiles(fileIds: List<Long>): List<File>

    @Query(
        """
        select native.file_id from files 
            join files native on 
                files.path = native.path and 
                files.file_name = native.file_name || ".bul" and
                files.project_id = native.project_id
        where files.file_id in (:fileIds)
    """
    )
    fun getNativeFiles(fileIds: List<Long>): List<Long>
}

data class SyncFile(
    @ColumnInfo(name = "bullet_file_id")
    val bulletsFile: Long,

    @ColumnInfo(name = "raw_file_id")
    val rawFile: Long
)