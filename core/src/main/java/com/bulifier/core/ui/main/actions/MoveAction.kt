package com.bulifier.core.ui.main.actions

import com.bulifier.core.db.File
import com.bulifier.core.db.FileDao
import com.bulifier.core.utils.Logger

suspend fun moveAction(file: File, newFileNameOrPath: String, db: FileDao, logger: Logger) {
    if (file.isFile) {
        moveFile(file, newFileNameOrPath, db, logger)
        val associatedFile = getAssociatedFile(file, db, logger)
        if (associatedFile == null) {
            logger.d("Associated file not found")
            return
        }
        val newAssociatedFileName = if (newFileNameOrPath.endsWith(".bul")) {
            newFileNameOrPath.removeSuffix(".bul")
        } else {
            "$newFileNameOrPath.bul"
        }
        moveFile(associatedFile, newAssociatedFileName, db, logger)
    } else {
        db.updateFolderName(file, newFileNameOrPath)
        logger.d("Folder renamed to: $newFileNameOrPath")
    }
}

private suspend fun getAssociatedFile(
    file: File,
    db: FileDao,
    logger: Logger
): File? {
    val associatedFileName = if (file.fileName.endsWith(".bul")) {
        file.fileName.removeSuffix(".bul")
    } else {
        "${file.fileName}.bul"
    }
    val associatedFile = db.getFile(file.path, associatedFileName, file.projectId)
    if (associatedFile == null) {
        logger.d("Associated file ${file.path}/$associatedFileName not found")
    }
    return associatedFile
}

private suspend fun moveFile(
    file: File,
    newFileNameOrPath: String,
    db: FileDao,
    logger: Logger
) {
    val newPath = if (newFileNameOrPath.trim().startsWith("/")) {
        newFileNameOrPath.substring(1)
    } else {
        newFileNameOrPath.trim()
    }

    val newFileName = newPath.substringAfterLast('/')
    val newFilePath = newPath.substringBeforeLast('/')
    db.updateFileName(file.copy(fileName = newFileName, path = newFilePath))
    logger.d("File renamed to: $newFileName at path: $newFilePath")
}

suspend fun deleteAction(file: File, db: FileDao, logger: Logger) {
    logger.d("Deleting file: ${file.path}/${file.fileName}")
    if (file.isFile) {
        listOf(file, getAssociatedFile(file, db, logger)).filterNotNull().forEach{
            db.deleteFile(it.fileId)
            logger.d("File deleted: ${it.path}/${it.fileName}")
        }
    } else {
        val fullPath = if (file.path.isEmpty()) file.fileName else "${file.path}/${file.fileName}"
        db.deleteFolder(file.fileId, fullPath, file.projectId)
        logger.d("Folder deleted: ${file.fileName}")
    }
}

suspend fun markForDeleteAction(file: File, db: FileDao, logger: Logger) {
    logger.d("Marking for deletion: ${file.path}/${file.fileName}")
    if (file.isFile) {
        listOf(file, getAssociatedFile(file, db, logger)).filterNotNull().forEach{
            db.markForDeleteAction(it.fileId)
            logger.d("File deleted: ${it.path}/${it.fileName}")
        }
    } else {
        db.markForDeleteAction(file.fileId, file.path + "/" + file.fileName, file.projectId)
        logger.d("Folder deleted: ${file.fileName}")
    }
}