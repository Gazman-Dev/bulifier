package com.bulifier.core.git

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.bulifier.core.db.db
import com.bulifier.core.prefs.AppSettings
import com.bulifier.core.prefs.AppSettings.project
import com.bulifier.core.prefs.PrefBooleanValue
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
        get() = File(app.filesDir, project.value.projectName)
    private val credentials = SecureCredentialManager(app)
    private val db by lazy { app.db.fileDao() }
    val autoCommit = PrefBooleanValue("autoCommit", true)

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
    val commits = project.flatMapLatest { project ->
        getCommitsPager(File(app.filesDir, project.projectName))
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
                AppSettings.appLogger.e(e)
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
            credentials.saveCredentials(project.value.projectId, username, passwordToken)
            val creds = fetchCredentials() ?: return@launch

            try {
                repoDir.deleteRecursively()
                GitHelper.clone(repoDir, creds, repoUrl)

                syncDbToLocal(false) // override repo with project files
                syncLocalToDb() // load all the files into the db

                AppSettings.scope.launch {
                    SchemaModel.verify(project.value)
                }

                resetGit(true)
            } catch (e: Exception) {
                reportError(e, "Clone")
            }
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
        db.dbToLocal(app, project.value.projectId, clearOldFiles)
    }

    private suspend fun syncLocalToDb() {
        db.localToDb(app, project.value.projectId)
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
        return credentials.retrieveCredentials(project.value.projectId).apply {
            if (this == null) {
                this@GitViewModel.setGitStatus(GitStatus.PROCESSING)
            }
        }
    }

    suspend fun getBranches() = try {
        GitHelper.branches(repoDir)
    } catch (e: Exception) {
        AppSettings.appLogger.e(e)
        emptyList()
    }

    suspend fun getTags() = try {
        GitHelper.tags(repoDir)
    } catch (e: Exception) {
        AppSettings.appLogger.e(e)
        emptyList()
    }

    suspend fun getCredentials() = credentials.retrieveCredentials(project.value.projectId)
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

    fun toggleAutoCommit() {
        autoCommit.set(!autoCommit.flow.value)
    }

    fun autoCommit() = autoCommit.flow.value && !isCloneNeeded()
}

data class GitError(
    val message: String,
    val title: String,
    val type: String
)