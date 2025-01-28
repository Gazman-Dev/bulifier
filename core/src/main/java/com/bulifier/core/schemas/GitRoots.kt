package com.bulifier.core.schemas

import com.bulifier.core.db.AppDatabase
import com.bulifier.core.db.Content
import com.bulifier.core.db.File
import com.bulifier.core.db.FileData

const val GIT_ROOTS_FILE_NAME = "git_roots.settings"

class GitRoots(
    private val db: AppDatabase,
    private val projectId: Long
) {

    private lateinit var rootsFile: File

    suspend fun load() {
        rootsFile = db.fileDao().getFile("", GIT_ROOTS_FILE_NAME, projectId) ?: File(
            path = "",
            fileName = GIT_ROOTS_FILE_NAME,
            isFile = true,
            projectId = projectId
        ).run {
            copy(fileId = db.fileDao().insertFile(this))
        }
    }

    suspend fun addRoot(path: String) {
        db.fileDao().getContent(rootsFile.fileId).let { content: FileData? ->
            if(content == null){
                db.fileDao().insertContentAndUpdateFileMetaData(
                    Content(rootsFile.fileId, path)
                )
            }
            else {
                db.fileDao().updateContentAndFileMetaData(
                    content.copy(content = addRoot(content.content, path)).toContent()
                )
            }
        }
    }


    private fun addRoot(content: String, root: String): String {
        // Split the existing content into a list of roots
        val existingRoots = content.lines().filter { it.isNotBlank() }.toMutableList()

        // Check if the new root is a subfolder of any existing root
        if (existingRoots.any { root.startsWith("$it/") }) {
            // If it's a subfolder, ignore it
            return content
        }

        // Remove any existing roots that are subfolders of the new root
        existingRoots.removeAll { it.startsWith("$root/") }

        // Add the new root
        existingRoots.add(root)

        // Return the updated content as a string with new lines
        return existingRoots.sorted().joinToString("\n")
    }
}