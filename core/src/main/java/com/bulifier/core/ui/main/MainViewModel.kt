package com.bulifier.core.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
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
import kotlinx.coroutines.Dispatchers
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

    val openedFile: LiveData<FileInfo?> = MutableLiveData()
    val fileContent = openedFile.switchMap { fileInfo ->
        if (fileInfo != null) {
            app.db.fileDao().getContentLiveData(fileInfo.fileId)
        } else {
            MutableLiveData(null as String?) as LiveData<String?>
        }
    }


    val fullPath = MediatorLiveData<FullPath>().apply {
        addSource(openedFile) {
            value = kotlin.run {
                val value = path.value
                FullPath(it?.fileName, extractPath(value))
            }
        }
        addSource(path) {
            value = FullPath(openedFile.value?.fileName, extractPath(it))
        }
    }

    private fun extractPath(value: String?): String {
        val path = if (!value.isNullOrBlank()) {
            "/$value"
        } else {
            ""
        }
        return "${projectName.value}$path"
    }

    val pagingDataFlow by lazy {
        MediatorLiveData<PagingData<File>>().apply {
            addSource(path) { newPath ->
                val currentProjectId = projectId.value ?: return@addSource
                updatePagingData(newPath, currentProjectId)
            }

            addSource(projectId) { newProjectId ->
                val currentPath = path.value ?: projectName.value ?: return@addSource
                updatePagingData(currentPath, newProjectId)
            }
        }
    }

    fun openFile(file: File) {
        viewModelScope.launch {
            val content = db.getContent(file.fileId) ?: FileData(
                fileId = file.fileId,
                fileName = file.fileName,
                path = file.path,
                content = "",
                type = Content.Type.NONE
            )
            openedFile as MutableLiveData
            openedFile.value = FileInfo(
                fileId = content.fileId,
                fileName = content.fileName
            )
        }
    }

    fun closeFile() {
        openedFile as MutableLiveData
        openedFile.value = null
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
                pagingDataFlow.value = it
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
            val projectId = projectId.value ?: return@launch
            val path = path.value ?: projectName.value ?: return@launch
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
            val projectId = projectId.value ?: return@launch
            val path = path.value ?: return@launch
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
        val openedFile = openedFile.value ?: return
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
            val projectId = projectId.value ?: return@withContext
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
}