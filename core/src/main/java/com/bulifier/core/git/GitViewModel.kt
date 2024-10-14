package com.bulifier.core.git

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bulifier.core.db.db
import com.bulifier.core.prefs.Prefs.projectId
import com.bulifier.core.prefs.Prefs.projectName
import com.bulifier.core.schemas.SchemaModel
import deleteAllFilesInFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.transport.CredentialsProvider

class GitViewModel(val app: Application) : AndroidViewModel(app) {
    private val repoDir: java.io.File
        get() = java.io.File(app.filesDir, projectName.flow.value)
    private val credentials = SecureCredentialManager(app)
    private val db by lazy { app.db.fileDao() }

    val gitInfo: StateFlow<String> = MutableStateFlow("idle")
    val gitErrors: SharedFlow<GitError> = MutableSharedFlow()


    init {
        viewModelScope.launch {
            resetGitInfo()
        }
    }

    private fun resetGitInfo() {
        viewModelScope.launch {
            gitInfo as MutableStateFlow
            gitInfo.value = if (GitHelper.isCloneNeeded(repoDir)) {
                "Git: Pending Clone"
            } else {
                try {
                    "Git: ${GitHelper.currentBranch(repoDir)}"
                } catch (e: Exception) {
                    e.printStackTrace()
                    "Git: Error " + e.message
                }
            }
        }
    }

    fun pull() {
        viewModelScope.launch {
            try {
                syncDbToLocal()
                val creds = fetchCredentials() ?: return@launch
                updateGitInfo("Pulling")
                GitHelper.pull(repoDir, creds)
                syncLocalToDb()
                markGitInfoSuccess()
            } catch (e: Exception) {
                reportError(e, "Pull")
            }
        }
    }

    fun push() {
        viewModelScope.launch {
            try {
                syncDbToLocal()
                if (!GitHelper.isClean(repoDir)) {
                    reportError("Commit before pushing", "Push Error")
                    return@launch
                }
                updateGitInfo("Pushing")
                val creds = fetchCredentials() ?: return@launch
                GitHelper.push(repoDir, creds)
                markGitInfoSuccess()
            } catch (e: Exception) {
                reportError(e, "Push")
            }
        }
    }

    private suspend fun reportError(message: String, title: String) {
        gitErrors as MutableSharedFlow
        gitErrors.emit(GitError(message, title, "error"))
        resetGitInfo()
    }

    fun clone(repoUrl: String, username: String, passwordToken: String) {
        viewModelScope.launch {
            updateGitInfo("Cloning")
            credentials.saveCredentials(projectId.flow.value, username, passwordToken)
            val creds = fetchCredentials() ?: return@launch

            try {
                deleteAllFilesInFolder(repoDir)
                GitHelper.clone(repoDir, creds, repoUrl)
                syncDbToLocal(false) // override repo with project files
                syncLocalToDb() // load all the files into the db
                updateGitInfo("Reloading schemas")
                SchemaModel.reloadSchemas(projectId.flow.value)
                markGitInfoSuccess()
            } catch (e: Exception) {
                reportError(e, "Clone")
            }
        }
    }

    private fun updateGitInfo(message: String) {
        gitInfo as MutableStateFlow
        gitInfo.value = message
    }

    suspend fun fetch() {
        updateGitInfo("Fetching")
        GitHelper.fetch(repoDir, fetchCredentials() ?: return)
        resetGitInfo()
    }

    fun commit(commitMessage: String) {
        viewModelScope.launch {
            try {
                syncDbToLocal()
                updateGitInfo("Committing")
                GitHelper.commit(repoDir, commitMessage)
                markGitInfoSuccess()
            } catch (e: Exception) {
                reportError(e, "Commit")
            }
        }
    }

    fun checkout(branchName: String, isNew: Boolean) {
        viewModelScope.launch {
            try {
                syncDbToLocal()
                if (!GitHelper.isClean(repoDir)) {
                    reportError("Commit before checking out", "Checkout Error")
                    return@launch
                }
                GitHelper.fetch(repoDir, fetchCredentials() ?: return@launch)
                updateGitInfo("Checking out")
                GitHelper.checkout(repoDir, branchName, isNew)
                syncLocalToDb()
                markGitInfoSuccess()
            } catch (e: Exception) {
                reportError(e, "Checkout")
            }
        }
    }

    private suspend fun syncDbToLocal(clearOldFiles: Boolean = true) {
        updateGitInfo("Syncing db to local storage")
        db.dbToLocal(app, projectId.flow.value, clearOldFiles)
    }

    private suspend fun syncLocalToDb() {
        updateGitInfo("Syncing local storage to db")
        db.localToDb(app, projectId.flow.value)
    }

    private suspend fun reportError(
        e: Exception,
        type: String
    ) {
        e.printStackTrace()
        gitErrors as MutableSharedFlow
        gitErrors.emit(
            GitError(
                message = e.message ?: "Unknown error",
                title = "$type Error",
                type = type.lowercase()
            )
        )
        resetGitInfo()
    }

    private suspend fun markGitInfoSuccess() {
        updateGitInfo("Success!")
        delay(1000)
        resetGitInfo()
    }

    private suspend fun fetchCredentials(): CredentialsProvider? {
        return credentials.retrieveCredentials(projectId.flow.value).apply {
            if (this == null) {
                updateGitInfo("Credentials error")
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
                java.io.File(app.filesDir, projectName).deleteRecursively()
            }
        }
    }

    fun clean() {
        viewModelScope.launch {
            updateGitInfo("Cleaning")
            GitHelper.clean(repoDir)
            syncLocalToDb()
            markGitInfoSuccess()

        }
    }
}

data class GitError(
    val message: String,
    val title: String,
    val type: String
)