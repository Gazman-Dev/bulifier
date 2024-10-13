package com.bulifier.core.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.CredentialsProvider
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
        credentials: CredentialsProvider
    ): MutableIterable<Any>? = withContext(Dispatchers.IO) {
        Git.open(repoDir).use { git ->
            git.push()
                .setCredentialsProvider(credentials)
                .call()
        }
    }

    suspend fun currentBranch(repoDir: File) = withContext(Dispatchers.IO) {
        Git.open(repoDir)?.repository?.branch
    }

    suspend fun branches(repoDir: File): List<String> = withContext(Dispatchers.IO) {
        Git.open(repoDir)
            ?.branchList()
            ?.call()
            ?.map { it.name.removePrefix("refs/heads/").lowercase() } ?: emptyList()
    }

    suspend fun tags(repoDir: File): List<String> = withContext(Dispatchers.IO) {
        Git.open(repoDir)
            ?.tagList()
            ?.call()
            ?.map { it.name.removePrefix("refs/tags/").lowercase() } ?: emptyList()
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

    suspend fun fetch(repoDir: File, credentials: CredentialsProvider){
        withContext(Dispatchers.IO) {
            Git.open(repoDir).use { git ->
                git
                    .fetch()
                    .setCredentialsProvider(credentials)
                    .call()
            }
        }
    }

    suspend fun checkout(repoDir: File, name: String, isNew: Boolean = false) {
        withContext(Dispatchers.IO) {
            Git.open(repoDir).use { git ->
                val repo = git.repository
                val branchList = branches(repoDir)
                val tagList = tags(repoDir)

                if (branchList.contains(name)) {
                    // It's a branch, check it out
                    git.checkout()
                        .setCreateBranch(isNew)
                        .setName(name)
                        .call()
                } else if (tagList.contains(name)) {
                    // It's a tag, check it out as a detached head
                    git.checkout()
                        .setName(repo.findRef("refs/tags/$name").objectId.name)
                        .call()
                } else {
                    // Name doesn't exist, create a new local branch
                    git.checkout()
                        .setCreateBranch(true)
                        .setName(name)
                        .call()
                }
            }
        }
    }


    fun isCloneNeeded(repoDir: File): Boolean {
        val parentDir = repoDir.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()  // Create the parent directories if they don't exist
        }
        return !repoDir.exists() || !File(repoDir, ".git").exists()
    }

    suspend fun commit(repoDir: File, commitMessage: String) {
        withContext(Dispatchers.IO) {
            Git.open(repoDir).use { git ->
                git.add()
                    .addFilepattern(".")
                    .setUpdate(true) // include deleted files
                    .call()
                git.commit()
                    .setMessage(commitMessage)
                    .call()
            }
        }
    }

    fun isClean(repoDir: File): Boolean {
        Git.open(repoDir).use { git ->
            val status = git.status().call()
            return status.isClean
        }
    }

}
