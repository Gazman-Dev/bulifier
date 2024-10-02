package com.bulifier.core.ui.main

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bulifier.core.db.Content
import com.bulifier.core.db.File
import com.bulifier.core.db.FileData
import com.bulifier.core.db.Project
import com.bulifier.core.db.db
import com.bulifier.core.prefs.Prefs
import com.bulifier.core.prefs.Prefs.path
import com.bulifier.core.prefs.Prefs.projectId
import com.bulifier.core.prefs.Prefs.projectName
import com.bulifier.core.schemas.SchemaModel
import com.bulifier.core.ui.utils.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val db by lazy { app.db.fileDao() }

    val projectsFlow = Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            maxSize = 100
        )
    ) {
        db.fetchProjects()
    }.flow.cachedIn(viewModelScope)

    private val _openedFile = MutableStateFlow<FileInfo?>(null)
    val openedFile: StateFlow<FileInfo?> = _openedFile

    private val _fileContent = MutableStateFlow("")
    val fileContent: StateFlow<String> = _fileContent

    init {
        viewModelScope.launch {
            _openedFile.collectLatest { fileInfo ->
                if (fileInfo != null) {
                    Log.d("MainViewModel", "open file ${fileInfo.fileId}")
                    app.db.fileDao().getContentFlow(fileInfo.fileId).collect{
                        Log.d("MainViewModel", "file content changed ${fileInfo.fileId}")
                        _fileContent.value = it?.content ?: ""
                    }
                } else {
                    Log.d("MainViewModel", "no fileContent")
                    _fileContent.value = ""
                }
            }
        }
    }

    private val _fullPath = MutableStateFlow(FullPath(null, ""))
    val fullPath: StateFlow<FullPath> = _fullPath

    init {
        // Combine flows to update fullPath when either path or openedFile changes
        viewModelScope.launch {
            combine(path.flow, _openedFile) { pathValue, fileInfo ->
                FullPath(fileInfo?.fileName, extractPath(pathValue))
            }.collect { newFullPath ->
                _fullPath.value = newFullPath
            }
        }
    }

    private fun extractPath(value: String?): String {
        val path = if (!value.isNullOrBlank()) {
            "/$value"
        } else {
            ""
        }
        return "${projectName.flow.value}$path"
    }

    private val _pagingDataFlow = MutableStateFlow<PagingData<File>>(PagingData.empty())
    val pagingDataFlow: StateFlow<PagingData<File>> = _pagingDataFlow

    init {
        // Combine projectId and path changes to update paging data
        viewModelScope.launch {
            combine(path.flow, projectId.flow) { newPath, newProjectId ->
                newPath to newProjectId
            }.collect { (newPath, newProjectId) ->
                updatePagingData(newPath, newProjectId)
            }
        }
    }

    fun openFile(file: File) {
        Log.d("MainViewModel", "openFile ${file.fileName}")
        viewModelScope.launch {
            val content = db.getContent(file.fileId) ?: FileData(
                fileId = file.fileId,
                fileName = file.fileName,
                path = file.path,
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
        db.getContent(fileId)?.let {
            copyToClipboard(app, it.content)
            Toast.makeText(app, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun closeFile() {
        Log.d("MainViewModel", "closeFile")
        _openedFile.value = null
    }

    private fun updatePagingData(currentPath: String, currentProjectId: Long) {
        viewModelScope.launch {
            Pager(
                config = PagingConfig(pageSize = 20),
                pagingSourceFactory = {
                    app.db.fileDao().fetchFilesByPathAndProjectId(
                        currentPath,
                        currentProjectId
                    )
                }
            ).flow.cachedIn(viewModelScope).collect {
                _pagingDataFlow.value = it
            }
        }
    }

    suspend fun createProject(projectName: String) {
        val projectId = db.insertProject(Project(projectName = projectName))
        withContext(Dispatchers.Main) {
            Prefs.projectId.set(projectId)
            Prefs.projectName.set(projectName)
            path.set("")
        }
    }

    suspend fun selectProject(project: Project) {
        withContext(Dispatchers.Main) {
            projectId.set(project.projectId)
            projectName.set(project.projectName)
            path.set("")
        }
    }

    fun updatePath(path: String) {
        Prefs.path.set(path)
    }

    fun updateCreateFile(fileName: String) {
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
        }
    }

    fun createFolder(folderName: String) {
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
                extraPathParts += it
            }
            updatePath((pathParts + extraPathParts).joinToString("/"))
        }
    }

    fun updateFileContent(content: String) {
        val openedFile = _openedFile.value ?: return
        viewModelScope.launch {
            db.insertContentAndUpdateFileSize(
                Content(
                    fileId = openedFile.fileId,
                    content = content
                )
            )
        }
    }

    fun shareFiles() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val projectId = projectId.flow.value
            val files = db.fetchFilesListByProjectId(projectId)
            MultiFileSharingUtil(app).shareFiles(files)
        }
    }

    fun deleteProject(project: Project) = viewModelScope.launch {
        db.deleteProject(project)
    }

    fun renameFile(file: File, newFileNameOrPath: String) = try {
        viewModelScope.launch {
            if (file.isFile) {
                val newPath = if(newFileNameOrPath.trim().startsWith("/")){
                    newFileNameOrPath.substring(1)
                }else{
                    newFileNameOrPath.trim()
                }

                val newFileName = newPath.substringAfterLast('/')
                val newFilePath = newPath.substringBeforeLast('/')
                db.updateFileName(file.copy(fileName = newFileName, path = newFilePath))
            }
            else{
                db.updateFolderName(file, newFileNameOrPath)
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    fun deleteFile(file: File) {
        viewModelScope.launch {
            if(file.isFile) {
                db.deleteFile(file.fileId)
            }
            else{
                db.deleteFolder(file.fileId, file.path + "/" + file.fileName)
            }
        }
    }

    fun reloadSchemas() {
        viewModelScope.launch {
            SchemaModel.reloadSchemas(projectId.flow.value)
        }
    }

    fun resetSystemSchemas() {
        viewModelScope.launch {
            SchemaModel.resetSystemSchemas(projectId.flow.value)
        }
    }
}
