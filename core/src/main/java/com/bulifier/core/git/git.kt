package com.bulifier.core.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.transport.CredentialsProvider
import java.io.File
import kotlin.math.min

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
        Git.open(repoDir)?.repository?.branch?.run {
            substring(0, min(10, length))
        }
    }

    suspend fun branches(repoDir: File): List<String> = withContext(Dispatchers.IO) {
        val list = Git.open(repoDir)
            ?.branchList()
            ?.setListMode(ListBranchCommand.ListMode.ALL)
            ?.call()
            ?.map { it.name.substringAfterLast("/").lowercase() } ?: emptyList()
        list.toSet().toList() // Remove duplicates
    }

    suspend fun tags(repoDir: File): List<String> = withContext(Dispatchers.IO) {
        Git.open(repoDir)
            ?.tagList()
            ?.call()
            ?.map { it.name.substringAfterLast("/").lowercase() } ?: emptyList()
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
                val remoteBranchRef = repo.findRef("refs/remotes/origin/$name")
                val localBranchRef = repo.findRef("refs/heads/$name")
                val ref = repo.findRef(name)

                if (isNew) {
                    git.checkout()
                        .setCreateBranch(true)
                        .setName(name)
                        .call()
                } else if (localBranchRef != null) {
                    git.checkout()
                        .setName(name)
                        .call()
                } else if(remoteBranchRef != null){
                    git.checkout()
                        .setCreateBranch(true)
                        .setName(name)
                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                        .setStartPoint("origin/$name")
                        .call()
                } else if (ref != null) {
                    // It's a tag, check it out as a detached head
                    git.checkout()
                        .setName(ref.objectId.name)
                        .call()
                } else{
                    throw IllegalArgumentException("Couldn't find branch or tag $name")
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

                git.add()
                    .addFilepattern(".")
                    .setUpdate(false) // include new files
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

    suspend fun clean(repoDir: File) {
        withContext(Dispatchers.IO) {
            Git.open(repoDir).use { git ->
                git.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .call()

                git.clean()
                    .setCleanDirectories(true)  // Removes untracked directories
                    .setIgnore(false)  // Removes ignored files as well
                    .call()
            }
        }
    }

}
