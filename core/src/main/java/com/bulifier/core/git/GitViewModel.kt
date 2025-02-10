package com.bulifier.core.git

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.bulifier.core.db.db
import com.bulifier.core.prefs.Prefs.projectId
import com.bulifier.core.prefs.Prefs.projectName
import com.bulifier.core.schemas.SchemaModel
import com.bulifier.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.transport.CredentialsProvider
import java.io.File

class GitViewModel(val app: Application) : AndroidViewModel(app) {
    private val repoDir: File
        get() = File(app.filesDir, projectName.flow.value)
    private val credentials = SecureCredentialManager(app)
    private val db by lazy { app.db.fileDao() }

    val gitStatus: StateFlow<GitStatus> = MutableStateFlow(GitStatus.IDLE)

    enum class GitStatus {
        IDLE,
        PROCESSING,
        SUCCESS,
        ERROR
    }

    val gitErrors: SharedFlow<GitError> = MutableSharedFlow()
    val branch: StateFlow<String> = MutableStateFlow("???")

    private val logger = Logger("GitViewModel")


    init {
        viewModelScope.launch {
            resetGit()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val commits = projectName
        .flow.flatMapLatest { project ->
            getCommitsPager(File(app.filesDir, project))
        }
        .cachedIn(viewModelScope)

    private fun getCommitsPager(repoDir: File) = Pager(
        config = PagingConfig(
            pageSize = 20, // Adjust page size as needed
            enablePlaceholders = false
        ),
        pagingSourceFactory = { CommitPagingSource(repoDir) }
    ).flow

    private suspend fun resetGit(showSuccess: Boolean = false) {
        if (showSuccess) {
            setGitStatus(GitStatus.SUCCESS)
            delay(1000)
        }
        viewModelScope.launch {
            try {
                updateBranch()
                setGitStatus(GitStatus.IDLE)
            } catch (e: Exception) {
                e.printStackTrace()
                "Git: Error " + e.message
            }
        }
    }

    private suspend fun updateBranch() {
        branch as MutableStateFlow
        if (GitHelper.isCloneNeeded(repoDir)) {
            branch.value = "???"
            return
        }
        branch.value = GitHelper.currentBranch(repoDir) ?: "???"
    }

    private fun setGitStatus(status: GitStatus) {
        gitStatus as MutableStateFlow
        gitStatus.value = status
    }

    fun pull() {
        viewModelScope.launch {
            try {
                setGitStatus(GitStatus.PROCESSING)
                syncDbToLocal()
                val creds = fetchCredentials() ?: return@launch
                GitHelper.pull(repoDir, creds)
                syncLocalToDb()
                resetGit(true)
            } catch (e: Exception) {
                reportError(e, "Pull")
            }
        }
    }

    fun push() {
        viewModelScope.launch {
            try {
                setGitStatus(GitStatus.PROCESSING)
                syncDbToLocal()
                if (!GitHelper.isClean(repoDir)) {
                    reportError("Commit before pushing", "Push Error")
                    return@launch
                }
                val creds = fetchCredentials() ?: return@launch
                GitHelper.push(repoDir, creds)
                resetGit(true)
            } catch (e: Exception) {
                reportError(e, "Push")
            }
        }
    }

    private suspend fun reportError(message: String, title: String) {
        gitErrors as MutableSharedFlow
        gitErrors.emit(GitError(message, title, "error"))
        setGitStatus(GitStatus.ERROR)
        delay(1000)
        resetGit()
    }

    fun clone(repoUrl: String, username: String, passwordToken: String) {
        viewModelScope.launch {
            setGitStatus(GitStatus.PROCESSING)
            credentials.saveCredentials(projectId.flow.value, username, passwordToken)
            val creds = fetchCredentials() ?: return@launch

            try {
                val backupDir = File(app.filesDir, "backup")
                backupFiles(repoDir, backupDir)

                repoDir.deleteRecursively()
                GitHelper.clone(repoDir, creds, repoUrl)
                restoreBackup(backupDir, repoDir)
                backupDir.deleteRecursively()
                backupDir.delete()

                syncDbToLocal(false) // override repo with project files
                syncLocalToDb() // load all the files into the db

                SchemaModel.verifySchemasRequest(projectId.flow.value)

                resetGit(true)
            } catch (e: Exception) {
                reportError(e, "Clone")
            }
        }
    }

    private fun backupFiles(repoDir: File, backupDir: File) {
        if (!repoDir.exists()) return

        backupDir.mkdirs()
        backupDir.deleteRecursively()

        repoDir.listFiles()?.forEach { file ->
            val target = File(backupDir, file.name)
            file.copyRecursively(target, overwrite = true)
        }
    }

    private fun restoreBackup(backupDir: File, repoDir: File) {
        backupDir.listFiles()?.forEach { file ->
            val target = File(repoDir, file.name)
            file.copyRecursively(target, overwrite = true)
        }
    }

    suspend fun fetch() {
        setGitStatus(GitStatus.PROCESSING)
        GitHelper.fetch(repoDir, fetchCredentials() ?: return)
        resetGit(true)
    }

    fun commit(commitMessage: String) {
        viewModelScope.launch {
            try {
                setGitStatus(GitStatus.PROCESSING)
                syncDbToLocal()
                GitHelper.commit(repoDir, commitMessage)
                resetGit(true)
            } catch (e: Exception) {
                reportError(e, "Commit")
            }
        }
    }

    fun checkout(branchName: String, isNew: Boolean) {
        viewModelScope.launch {
            try {
                setGitStatus(GitStatus.PROCESSING)
                syncDbToLocal()
                if (!GitHelper.isClean(repoDir)) {
                    reportError("Commit before checking out", "Checkout Error")
                    return@launch
                }
                GitHelper.fetch(repoDir, fetchCredentials() ?: return@launch)
                GitHelper.checkout(repoDir, branchName, isNew)
                syncLocalToDb()
                resetGit(true)
            } catch (e: Exception) {
                reportError(e, "Checkout")
            }
        }
    }

    private suspend fun syncDbToLocal(clearOldFiles: Boolean = true) {
        db.dbToLocal(app, projectId.flow.value, clearOldFiles)
    }

    private suspend fun syncLocalToDb() {
        db.localToDb(app, projectId.flow.value)
    }

    private suspend fun reportError(
        e: Exception,
        type: String
    ) {
        logger.e(e)
        gitErrors as MutableSharedFlow
        gitErrors.emit(
            GitError(
                message = e.message ?: "Unknown error",
                title = "$type Error",
                type = type.lowercase()
            )
        )
        resetGit()
    }

    private suspend fun fetchCredentials(): CredentialsProvider? {
        return credentials.retrieveCredentials(projectId.flow.value).apply {
            if (this == null) {
                this@GitViewModel.setGitStatus(GitStatus.PROCESSING)
            }
        }
    }

    suspend fun getBranches() = try {
        GitHelper.branches(repoDir)
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    suspend fun getTags() = try {
        GitHelper.tags(repoDir)
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    suspend fun getCredentials() = credentials.retrieveCredentials(projectId.flow.value)
    fun deleteProject(projectName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                File(app.filesDir, projectName).deleteRecursively()
            }
        }
    }

    fun clean() {
        viewModelScope.launch {
            try {
                logger.d("Cleaning")
                setGitStatus(GitStatus.PROCESSING)
                GitHelper.clean(repoDir)
                syncLocalToDb()
                resetGit(true)
            } catch (e: Exception) {
                reportError(e, "Clean")
            }
        }
    }

    fun cleanAndRevert(commitHash: String) {
        viewModelScope.launch {
            try {
                logger.d("Reverting to $commitHash")
                setGitStatus(GitStatus.PROCESSING)
                GitHelper.resetAndRevert(commitHash, repoDir)
                syncLocalToDb()
                resetGit(true)
            } catch (e: Exception) {
                reportError(e, "Revert")
            }
        }
    }

    fun isCloneNeeded() = GitHelper.isCloneNeeded(repoDir)
}

data class GitError(
    val message: String,
    val title: String,
    val type: String
)