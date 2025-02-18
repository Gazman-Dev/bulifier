package com.bulifier.core.ui.main

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.graphics.Insets
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.bulifier.core.db.Content
import com.bulifier.core.db.Dependency
import com.bulifier.core.db.File
import com.bulifier.core.db.Project
import com.bulifier.core.db.db
import com.bulifier.core.db.getBinaryFile
import com.bulifier.core.prefs.AppSettings
import com.bulifier.core.prefs.AppSettings.path
import com.bulifier.core.prefs.PrefBooleanValue
import com.bulifier.core.schemas.SchemaModel
import com.bulifier.core.ui.main.actions.deleteAction
import com.bulifier.core.ui.main.actions.moveAction
import com.bulifier.core.ui.utils.copyToClipboard
import com.bulifier.core.utils.Logger
import com.bulifier.core.utils.fullPath
import com.bulifier.core.utils.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.io.File as JavaFile

data class FullPath(
    val fileName: String?,
    val path: String
) {
    val fullPath: String
        get() = fullPath(path, fileName ?: "")
}

data class FileInfo(
    val fileId: Long,
    val fileName: String,
    var editing: Boolean = false
)

data class AppInsets(
    val imeInsets: Insets, val systemBars: Insets
)

class MainViewModel(val app: Application) : AndroidViewModel(app) {

    private val logger = Logger("MainViewModel")
    private val db by lazy { app.db.fileDao() }
    private val _openedFile = MutableStateFlow<FileInfo?>(null)
    private val _fileContent = MutableStateFlow("")
    private val _fullPath = MutableStateFlow(FullPath(null, ""))
    val fullScreenMode = MutableStateFlow(false)

    val appInsets: Flow<AppInsets> = MutableStateFlow(AppInsets(Insets.NONE, Insets.NONE))

    val wrapping = PrefBooleanValue("text wrapping")

    val openedFile: StateFlow<FileInfo?> = _openedFile
    val fileContent: StateFlow<String> = _fileContent
    val fullPath: StateFlow<FullPath> = _fullPath

    fun setInsets(imeInsets: Insets, systemBars: Insets) {
        appInsets as MutableStateFlow
        appInsets.value = AppInsets(imeInsets, systemBars)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagingDataFlow =
        combine(path.flow, AppSettings.project) { newPath: String, newProjectId: Project ->
            newPath to newProjectId
        }.flatMapLatest { (currentPath, currentProject) ->
            Pager(
                config = PagingConfig(pageSize = 20),
                pagingSourceFactory = {
                    app.db.fileDao().fetchFilesByPathAndProjectId(
                        currentPath,
                        currentProject.projectId
                    )
                }
            ).flow
        }.cachedIn(viewModelScope)

    val projectsFlow = Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            maxSize = 100
        )
    ) {
        db.fetchProjects()
    }.flow.cachedIn(viewModelScope)

    init {
        logger.i("MainViewModel initialized")

        viewModelScope.launch {
            _openedFile.collectLatest { fileInfo ->
                if (fileInfo != null) {
                    logger.d("Opening file ${fileInfo.fileId}")
                    app.db.fileDao().getContentFlow(fileInfo.fileId).collectLatest {
                        logger.d("File content updated for ${fileInfo.fileId}")
                        _fileContent.value = it?.content ?: ""
                    }
                } else {
                    logger.d("No file content available")
                    _fileContent.value = ""
                }
            }
        }

        viewModelScope.launch {
            combine(
                path.flow,
                _openedFile,
                AppSettings.project
            ) { pathValue, fileInfo, project ->
                val fullPath = if (pathValue.isNotBlank()) {
                    "/$pathValue"
                } else {
                    ""
                }
                FullPath(fileInfo?.fileName, "${project.projectName}$fullPath")
            }.collectLatest { newFullPath ->
                logger.d("Full path updated to $newFullPath")
                _fullPath.value = newFullPath
            }
        }
    }

    suspend fun openFileByPath(path: String): Boolean {
        val fileName = path.substringAfterLast("/")
        val path = path.substringBeforeLast("/", "")
        db.getFile(path, fileName, AppSettings.project.value.projectId)?.let {
            _openedFile.value = FileInfo(
                fileId = it.fileId,
                fileName = it.fileName
            )
            return true
        }
        return false
    }

    fun openFile(file: File) {
        logger.i("openFile", bundleOf("fileName" to file.fileName, "fileId" to file.fileId))
        viewModelScope.launch {
            val file = db.getFile(file.fileId)
            _openedFile.value = FileInfo(
                fileId = file.fileId,
                fileName = file.fileName
            )
        }
    }

    fun loadContentToClipboard(fileId: Long) = viewModelScope.launch {
        logger.i("loadContentToClipboard", bundleOf("fileId" to fileId))
        db.getContent(fileId)?.let {
            copyToClipboard(app, it.content)
            logger.d("Content copied to clipboard for fileId: $fileId")
            showToast("Copied to clipboard")
        }
    }

    fun closeFile() {
        logger.i("closeFile")
        _openedFile.value = null
    }

    suspend fun createUpdateOrSelectProject(
        projectName: String,
        projectDetails: String? = null,
        template: String? = null
    ) {
        logger.i("createOrSelectProject", bundleOf("projectName" to projectName))
        val existingProject = db.getProject(projectName)
        if (existingProject != null) {
            logger.d("Project already exists: $projectName")
            if (existingProject.projectDetails != projectDetails || existingProject.template != template) {
                val newProject =
                    existingProject.copy(projectDetails = projectDetails, template = template)
                db.updateProject(newProject)
                selectProject(newProject)
                logger.d("Project details updated: $projectName")
            } else {
                selectProject(existingProject)
            }
            return
        }
        val project = Project(
            projectName = projectName,
            projectDetails = projectDetails,
            template = template
        )
        val projectId = db.createProject(app, project, logger)
        logger.d("New project created with ID: $projectId")
        withContext(Dispatchers.Main) {
            AppSettings.updateProject(project.copy(projectId = projectId))
        }
    }

    suspend fun selectProject(project: Project) {
        logger.i("selectProject", bundleOf("projectId" to project.projectId))
        withContext(Dispatchers.Main) {
            AppSettings.updateProject(project)
        }
    }

    fun updatePath(path: String) {
        logger.i("updatePath", bundleOf("path" to path))
        AppSettings.path.set(path)
    }

    fun updateFileContent(content: String) {
        val openedFile = _openedFile.value ?: return
        viewModelScope.launch {
            logger.d("Updating content for fileId: ${openedFile.fileId}")
            db.insertContentAndUpdateFileMetaData(
                Content(
                    fileId = openedFile.fileId,
                    content = content
                )
            )
        }
    }

    fun deleteProject(project: Project) = viewModelScope.launch {
        logger.i("deleteProject", bundleOf("projectId" to project.projectId))
        if (project.projectId == AppSettings.project.value.projectId) {
            AppSettings.clear()
        }
        db.deleteProject(project)
        logger.d("Project deleted: ${project.projectId}")
    }

    fun moveFile(file: File, newFileNameOrPath: String) = try {
        logger.i("renameFile")
        viewModelScope.launch {
            moveAction(file, newFileNameOrPath, db, logger)
        }
        true
    } catch (e: Exception) {
        logger.e("Error renaming file/folder", e)
        false
    }

    fun deleteFile(file: File) {
        logger.i("deleteFile")
        viewModelScope.launch {
            deleteAction(file, db, logger)
        }
    }

    fun createFolder(folderName: String) {
        logger.i("createFolder", bundleOf("folderName" to folderName))
        viewModelScope.launch {
            val projectId = AppSettings.project.value.projectId
            val path = path.flow.value
            val extraPathParts = mutableListOf<String>()
            val pathParts = path.split("[^a-zA-Z0-9]".toRegex()).filter { it.isNotBlank() }
            folderName.split("[^a-zA-Z0-9]".toRegex()).forEach {
                db.insertFile(
                    File(
                        fileName = it,
                        projectId = projectId,
                        isFile = false,
                        path = (pathParts + extraPathParts).joinToString("/")
                    )
                )
                logger.d("Folder created: $it in path: ${(pathParts + extraPathParts).joinToString("/")}")
                extraPathParts += it
            }
            updatePath((pathParts + extraPathParts).joinToString("/"))
        }
    }

    fun reloadSchemas() {
        logger.i("reloadSchemas")
        viewModelScope.launch {
            SchemaModel.reloadSchemas(AppSettings.project.value)
            logger.d("Schemas reloaded for projectId: ${AppSettings.project.value.projectId}")
            showToast("Schemas reloaded")
        }
    }

    fun resetSystemSchemas() {
        logger.i("resetSystemSchemas")
        viewModelScope.launch {
            SchemaModel.resetSystemSchemas(AppSettings.project.value)
            logger.d("System schemas reset for projectId: ${AppSettings.project.value.projectId}")
        }
    }

    suspend fun isProjectEmpty(): Boolean {
        val isEmpty = db.isProjectEmpty(AppSettings.project.value.projectId)
        logger.d("Project is empty: $isEmpty")
        return isEmpty
    }

    suspend fun wasProjectJustUpdated(): Boolean {
        if (AppSettings.project.value.projectId == -1L) {
            logger.d("Project was not updated: projectId is -1")
            return false
        }
        val lastUpdated =
            db.getProject(AppSettings.project.value.projectId)?.lastUpdated?.time ?: 0L
        val wasUpdated = (Date().time - lastUpdated) < 5000
        logger.d("Project was just updated: $wasUpdated")

        return wasUpdated
    }

    suspend fun projectNames() = db.projectNames()

    suspend fun getProject(projectName: String) = db.getProject(projectName)

    fun getFileExtension(uri: Uri): String {
        // First attempt: Query the display name from the ContentResolver.
        var extension: String? = null
        if (uri.scheme == "content") {
            app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    val fileName = cursor.getString(nameIndex)
                    // Extract extension (if any) from the file name.
                    extension = fileName.substringAfterLast('.', "")
                }
            }
        }

        // Second attempt: Use the MIME type to get an extension.
        if (extension.isNullOrBlank()) {
            val mimeType = app.contentResolver.getType(uri)
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        }

        // Fallback if no extension found.
        if (extension.isNullOrBlank()) {
            extension = "dat"
        }
        return extension
    }

    /**
     * Ensures correct mapping for common file types.
     */
    private fun normalizeFileExtension(extension: String?): String? {
        return when (extension?.lowercase()) {
            "jpeg" -> "jpg"
            "tif" -> "tiff"
            "svgz" -> "svg"
            "htm" -> "html"
            "xht" -> "xhtml"
            "ogv" -> "ogg" // Some systems use .ogv for video/ogg
            "woff2", "woff", "ttf", "otf" -> extension // Keep as is
            "png", "jpg", "gif", "webp", "svg",
            "mp4", "webm", "ogg", "ico" -> extension // Keep as is
            else -> extension // Return as is or null
        }
    }

    fun saveFile(data: Intent?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val uri: Uri? = data?.data
                val path = path.flow.value

                if (uri == null) {
                    logger.d("No URI found in Intent")
                } else {
                    val extension = getFileExtension(uri)
                    val normalizedExtension = normalizeFileExtension(extension)
                    val fileName = "${System.currentTimeMillis()}.$normalizedExtension"

                    db.transaction {
                        val id = db.insertFile(
                            File(
                                fileName = fileName,
                                projectId = AppSettings.project.value.projectId,
                                isFile = true,
                                path = path,
                                isBinary = true
                            )
                        )

                        val destination = getBinaryFile(app, id)
                        destination.parentFile?.mkdirs()
                        val size = saveFile(destination, uri)
                        if (size != -1L) {
                            db.updateFileMetaData(id, size, -1)
                        }
                    }
                }
            }
        }
    }

    private fun saveFile(destination: JavaFile, uri: Uri): Long {
        app.contentResolver.openInputStream(uri)?.use { inputStream ->
            destination.outputStream().use { outputStream ->
                return inputStream.copyTo(outputStream)
            }
        } ?: run {
            logger.d("Failed to open InputStream from URI: $uri")
            logger.e("Failed to open InputStream from URI")
        }

        return -1
    }

    fun addImport(file: File) = viewModelScope.launch {
        val fileId = openedFile.value?.fileId
        if (fileId == null) {
            return@launch
        }
        db.addImport(
            Dependency(
                fileId = fileId,
                dependencyFileId = file.fileId,
                projectId = file.projectId
            )
        )
    }

    fun removeImport(file: File) = viewModelScope.launch {
        val fileId = openedFile.value?.fileId
        if (fileId == null) {
            return@launch
        }
        db.removeImport(
            fileId = fileId,
            dependencyFileId = file.fileId,
            projectId = file.projectId
        )
    }

    suspend fun getImports(): List<File> {
        val fileId = openedFile.value?.fileId
        if (fileId == null) {
            return emptyList()
        }
        return db.getImports(fileId, AppSettings.project.value.projectId)
    }

    suspend fun getAllImports(): List<File> {
        val fileId = openedFile.value?.fileId
        if (fileId == null) {
            return emptyList()
        }
        return db.getAllImports(fileId, AppSettings.project.value.projectId)
    }
}
