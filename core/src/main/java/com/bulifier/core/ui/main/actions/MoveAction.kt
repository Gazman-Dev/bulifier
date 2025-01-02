package com.bulifier.core.ui.main.actions

import com.bulifier.core.db.File
import com.bulifier.core.db.FileDao
import com.bulifier.core.utils.Logger

suspend fun moveAction(file: File, newFileNameOrPath: String, db: FileDao, logger: Logger) {
    if (file.isFile) {
        val newPath = if (newFileNameOrPath.trim().startsWith("/")) {
            newFileNameOrPath.substring(1)
        } else {
            newFileNameOrPath.trim()
        }

        val newFileName = newPath.substringAfterLast('/')
        val newFilePath = newPath.substringBeforeLast('/')
        db.updateFileName(file.copy(fileName = newFileName, path = newFilePath))
        logger.d("File renamed to: $newFileName at path: $newFilePath")
    } else {
        db.updateFolderName(file, newFileNameOrPath)
        logger.d("Folder renamed to: $newFileNameOrPath")
    }
}

suspend fun deleteAction(file: File, db: FileDao, logger: Logger) {
    if (file.isFile) {
        db.deleteFile(file.fileId)
        logger.d("File deleted: ${file.fileId}")
    } else {
        db.deleteFolder(file.fileId, file.path + "/" + file.fileName, file.projectId)
        logger.d("Folder deleted: ${file.fileName}")
    }
}

suspend fun markForDeleteAction(file: File, db: FileDao, logger: Logger) {
    if (file.isFile) {
        db.markForDeleteAction(file.fileId)
        logger.d("File deleted: ${file.fileId}")
    } else {
        db.markForDeleteAction(file.fileId, file.path + "/" + file.fileName, file.projectId)
        logger.d("Folder deleted: ${file.fileName}")
    }
}