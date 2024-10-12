package com.bulifier.core.git

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bulifier.core.db.db
import com.bulifier.core.prefs.Prefs.projectId
import com.bulifier.core.prefs.Prefs.projectName
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
                updateGitInfo("Syncing Local Storage")
                db.dbToFiles(app, projectId.flow.value)
                val creds = fetchCredentials() ?: return@launch
                updateGitInfo("Pulling")
                GitHelper.pull(repoDir, creds)
                updateGitInfo("Syncing DB")
                db.filesToDb(app, projectId.flow.value)
                markGitInfoSuccess()
            } catch (e: Exception) {
                reportError(e, "Pull")
            }
        }
    }

    fun push() {
        viewModelScope.launch {
            try {
                db.dbToFiles(app, projectId.flow.value)
                if(!GitHelper.isClean(repoDir)){
                    sendError("Commit before pushing", "Push Error")
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

    private suspend fun sendError(message: String, title: String) {
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
                GitHelper.clone(repoDir, creds, repoUrl)
                updateGitInfo("Syncing DB")
                db.filesToDb(app, projectId.flow.value)
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

    suspend fun fetch(){
        updateGitInfo("Fetching")
        GitHelper.fetch(repoDir, fetchCredentials() ?: return)
        resetGitInfo()
    }

    fun commit(commitMessage: String){
        viewModelScope.launch {
            try {
                updateGitInfo("Syncing local storage")
                db.dbToFiles(app, projectId.flow.value)
                updateGitInfo("Committing")
                withContext(Dispatchers.IO) {
                    GitHelper.commit(repoDir, commitMessage)
                }
                markGitInfoSuccess()
            } catch (e: Exception) {
                reportError(e, "Commit")
            }
        }
    }

    fun checkout(branchName: String, isNew:Boolean) {
        viewModelScope.launch {
            try {
                updateGitInfo("Syncing local storage")
                db.dbToFiles(app, projectId.flow.value)
                if(!GitHelper.isClean(repoDir)){
                    sendError("Commit before checking out", "Checkout Error")
                    return@launch
                }
                updateGitInfo("Checking out")
                GitHelper.checkout(repoDir, branchName, isNew)
                updateGitInfo("Syncing DB")
                db.filesToDb(app, projectId.flow.value)
                markGitInfoSuccess()
            } catch (e: Exception) {
                reportError(e, "Checkout")
            }
        }
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

    fun deleteGit() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                deleteAllFilesInFolder(repoDir)
                resetGitInfo()
            }
        }
    }

    suspend fun getCredentials() = credentials.retrieveCredentials(projectId.flow.value)
}

data class GitError(
    val message: String,
    val title: String,
    val type: String
)