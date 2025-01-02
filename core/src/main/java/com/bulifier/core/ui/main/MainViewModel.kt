package com.bulifier.core.ui.main

import android.app.Application
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.bulifier.core.db.Content
import com.bulifier.core.db.File
import com.bulifier.core.db.FileData
import com.bulifier.core.db.Project
import com.bulifier.core.db.db
import com.bulifier.core.prefs.PrefBooleanValue
import com.bulifier.core.prefs.Prefs
import com.bulifier.core.prefs.Prefs.path
import com.bulifier.core.prefs.Prefs.projectId
import com.bulifier.core.prefs.Prefs.projectName
import com.bulifier.core.schemas.SchemaModel
import com.bulifier.core.ui.main.actions.deleteAction
import com.bulifier.core.ui.main.actions.moveAction
import com.bulifier.core.ui.utils.copyToClipboard
import com.bulifier.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

data class FullPath(
    val fileName: String?,
    val path: String
)

data class FileInfo(
    val fileId: Long,
    val fileName: String,
    var editing: Boolean = false
)

class MainViewModel(val app: Application) : AndroidViewModel(app) {

    private val logger = Logger("MainViewModel")
    private val db by lazy { app.db.fileDao() }
    private val _openedFile = MutableStateFlow<FileInfo?>(null)
    private val _fileContent = MutableStateFlow("")
    private val _fullPath = MutableStateFlow(FullPath(null, ""))
    val fullScreenMode = MutableStateFlow(false)

    val wrapping = PrefBooleanValue("text wrapping")

    val openedFile: StateFlow<FileInfo?> = _openedFile
    val fileContent: StateFlow<String> = _fileContent
    val fullPath: StateFlow<FullPath> = _fullPath

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagingDataFlow = combine(path.flow, projectId.flow) { newPath, newProjectId ->
        newPath to newProjectId
    }.flatMapLatest { (currentPath, currentProjectId) ->
        Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = {
                app.db.fileDao().fetchFilesByPathAndProjectId(
                    currentPath,
                    currentProjectId
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
            combine(path.flow, _openedFile, projectName.flow) { pathValue, fileInfo, projectNameValue ->
                val fullPath = if (pathValue.isNotBlank()) {
                    "/$pathValue"
                } else {
                    ""
                }
                FullPath(fileInfo?.fileName, "$projectNameValue$fullPath")
            }.collectLatest { newFullPath ->
                logger.d("Full path updated to $newFullPath")
                _fullPath.value = newFullPath
            }
        }
    }

    fun openFile(file: File) {
        logger.i("openFile", bundleOf("fileName" to file.fileName, "fileId" to file.fileId))
        viewModelScope.launch {
            val content = db.getContent(file.fileId) ?: FileData(
                fileId = file.fileId,
                fileName = file.fileName,
                path = file.path,
                isFile = true,
                content = "",
                type = Content.Type.NONE
            )
            _openedFile.value = FileInfo(
                fileId = content.fileId,
                fileName = content.fileName
            )
        }
    }

    fun loadContentToClipboard(fileId: Long) = viewModelScope.launch {
        logger.i("loadContentToClipboard", bundleOf("fileId" to fileId))
        db.getContent(fileId)?.let {
            copyToClipboard(app, it.content)
            logger.d("Content copied to clipboard for fileId: $fileId")
            Toast.makeText(app, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun closeFile() {
        logger.i("closeFile")
        _openedFile.value = null
    }

    suspend fun createUpdateOrSelectProject(projectName: String, projectDetails: String? = null) {
        logger.i("createOrSelectProject", bundleOf("projectName" to projectName))
        val existingProject = db.getProject(projectName)
        if (existingProject != null) {
            logger.d("Project already exists: $projectName")
            if (existingProject.projectDetails != projectDetails) {
                db.updateProject(existingProject.copy(projectDetails = projectDetails))
                logger.d("Project details updated: $projectName")
            }
            selectProject(existingProject)
            return
        }
        val project = Project(projectName = projectName, projectDetails = projectDetails)
        val projectId = db.insertProject(project)
        logger.d("New project created with ID: $projectId")
        withContext(Dispatchers.Main) {
            Prefs.updateProject(project.copy(projectId = projectId))
        }
    }

    suspend fun selectProject(project: Project) {
        logger.i("selectProject", bundleOf("projectId" to project.projectId))
        withContext(Dispatchers.Main) {
            Prefs.updateProject(project)
        }
    }

    fun updatePath(path: String) {
        logger.i("updatePath", bundleOf("path" to path))
        Prefs.path.set(path)
    }

    fun updateCreateFile(fileName: String) {
        logger.i("updateCreateFile", bundleOf("fileName" to fileName))
        viewModelScope.launch {
            val projectId = projectId.flow.value
            val path = path.flow.value
            db.insertFile(
                File(
                    fileName = fileName,
                    projectId = projectId,
                    isFile = true,
                    path = path
                )
            )
            logger.d("File created: $fileName at path: $path")
        }
    }

    fun createFolder(folderName: String) {
        logger.i("createFolder", bundleOf("folderName" to folderName))
        viewModelScope.launch {
            val projectId = projectId.flow.value
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

    fun reloadSchemas() {
        logger.i("reloadSchemas")
        viewModelScope.launch {
            SchemaModel.reloadSchemas(projectId.flow.value)
            logger.d("Schemas reloaded for projectId: ${projectId.flow.value}")
            Toast.makeText(app, "Schemas reloaded", Toast.LENGTH_SHORT).show()
        }
    }

    fun resetSystemSchemas() {
        logger.i("resetSystemSchemas")
        viewModelScope.launch {
            SchemaModel.resetSystemSchemas(projectId.flow.value)
            logger.d("System schemas reset for projectId: ${projectId.flow.value}")
        }
    }

    suspend fun isProjectEmpty(): Boolean {
        val isEmpty = db.isProjectEmpty(projectId.flow.value)
        logger.d("Project is empty: $isEmpty")
        return isEmpty
    }

    suspend fun wasProjectJustUpdated(): Boolean {
        if (projectId.flow.value == -1L) {
            logger.d("Project was not updated: projectId is -1")
            return false
        }
        val lastUpdated = db.getProject(projectId.flow.value)
        val wasUpdated = (Date().time - lastUpdated.lastUpdated.time) < 5000
        logger.d("Project was just updated: $wasUpdated")

        return wasUpdated
    }

    suspend fun isProjectExists(projectName: String): Boolean {
        val exists = db.isProjectExists(projectName)
        logger.d("Project exists: $exists")
        return exists
    }
}
