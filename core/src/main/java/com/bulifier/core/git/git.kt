package com.bulifier.core.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.FetchResult
import java.io.File

object GitHelper {

    suspend fun pull(
        repoDir: File,
        credentials: CredentialsProvider
    ) {
        withContext(Dispatchers.IO) {
            Git.open(repoDir).use { git ->
                git.pull()
                    .setCredentialsProvider(credentials)
                    .call()
            }
        }
    }

    suspend fun push(
        repoDir: File,
        credentials: CredentialsProvider,
        commitMessage: String = "Made some changes"
    ) = withContext(Dispatchers.IO) {
        Git.open(repoDir).use { git ->
            git.add()
                .addFilepattern(".")
                .call()

            git.commit()
                .setMessage(commitMessage)
                .call()

            git.push()
                .setCredentialsProvider(credentials)
                .call()
        }
    }

    suspend fun currentBranch(repoDir: File) = withContext(Dispatchers.IO) {
        Git.open(repoDir)?.repository?.branch
    }

    suspend fun branches(repoDir: File) = withContext(Dispatchers.IO) {
        Git.open(repoDir)
            ?.branchList()
            ?.call()?.map {
                it.name
            } ?: emptyList<String>()
    }

    suspend fun clone(
        repoDir: File,
        credentials: CredentialsProvider,
        repoUri: String
    ): Git = withContext(Dispatchers.IO) {
        Git.cloneRepository()
            .setURI(repoUri)
            .setDirectory(repoDir)
            .setCredentialsProvider(credentials)
            .call()
    }

    suspend fun checkout(
        repoDir: File,
        credentials: CredentialsProvider,
        branch: String
    ) {
        withContext(Dispatchers.IO) {
            pull(repoDir, credentials)
            Git.open(repoDir)
                .checkout()
                .setName(branch)
                .call()
        }
    }

    fun isCloneNeeded(repoDir: File): Boolean {
        val parentDir = repoDir.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()  // Create the parent directories if they don't exist
        }
        return !repoDir.exists() || !File(repoDir, ".git").exists()
    }

}
