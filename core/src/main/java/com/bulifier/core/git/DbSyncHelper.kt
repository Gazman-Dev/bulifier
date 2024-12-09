import android.content.Context
import com.bulifier.core.db.Content
import com.bulifier.core.db.FileData
import com.bulifier.core.db.db
import java.io.File
import com.bulifier.core.db.File as DbFile

class DbSyncHelper(
    private val context: Context
) {

    private val db = context.db

    suspend fun importProject(projectId: Long) {
        // Fetch the project name from the database
        val projectName = db.fileDao().getProjectById(projectId).projectName

        // Define the project directory
        val projectDir = File(context.filesDir, projectName)

        db.fileDao().deleteFilesByProjectId(projectId)

        processDirectory(projectDir, "", projectId)
    }

    private suspend fun processDirectory(currentDir: File, parentPath: String, projectId: Long) {
        if (!currentDir.exists()) return

        currentDir.listFiles()?.forEach { file ->
            val fileName = file.name
            val isFile = file.isFile
            val size = if (isFile) file.length().toInt() else 0
            val path = parentPath

            if (fileName.lowercase().trim() == ".git") {
                return@forEach
            }
            if (isFile && !isTextFile(file)) {
                return@forEach
            }

            // Create and insert the File entity
            val fileEntity = DbFile(
                projectId = projectId,
                path = path,
                fileName = fileName,
                isFile = isFile,
                size = size
            )
            val fileId = db.fileDao().insertFile(fileEntity)

            if (isFile) {
                val content = file.readText()
                val contentEntity = Content(
                    fileId = fileId,
                    content = content,
                    type = Content.Type.NONE // Adjust the type as needed
                )
                db.fileDao().insertContent(contentEntity)
            } else {
                // Insert the directory with isFile = false
                // Calculate the new parent path
                val newParentPath = if (parentPath.isEmpty()) fileName else "$parentPath/$fileName"
                // Recursively process the subdirectory
                processDirectory(file, newParentPath, projectId)
            }
        }
    }


    suspend fun exportProject(projectId: Long, clearOldFiles: Boolean) {
        // Fetch the project name from the database or pass it as a parameter
        val projectName = db.fileDao().getProjectById(projectId).projectName
        val srcDir = File(context.filesDir, "$projectName/").apply {
            context.filesDir.mkdirs()
        }

        val files: List<FileData> = db.fileDao().exportFilesAndContents(projectId)
        files.mapNotNull {
            if (it.isFile) {
                null
            } else {
                it.path + "/" + it.fileName
            }
        }.toSet().forEach {
            File(srcDir, it).apply {
                mkdirs()
                if (clearOldFiles) {
                    deleteAllFilesInFolder(this)
                }
            }
        }


        files.forEach { fileData ->
            exportFile(
                baseDir = srcDir,
                relativePath = fileData.path,
                fileName = fileData.fileName,
                content = fileData.content,
                isFile = fileData.isFile
            )
        }
    }

    // Shared function for exporting files
    private fun exportFile(
        baseDir: File,
        relativePath: String,
        fileName: String,
        content: String,
        isFile: Boolean
    ) {
        val filePath = if (relativePath.isNotEmpty()) {
            File(baseDir, "${relativePath}/${fileName}")
        } else {
            File(baseDir, fileName)
        }

        if (isFile) {
            filePath.parentFile?.mkdirs()
            filePath.writeText(content)
        } else {
            filePath.mkdirs()
        }
    }
}

fun deleteAllFilesInFolder(folder: File) {
    if (folder.isDirectory) {
        val files = folder.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    deleteAllFilesInFolder(file)
                    file.delete()
                } else {
                    file.delete()
                }
            }
        }
    }
}

fun isTextFile(file: File, sampleSize: Int = 1024): Boolean {
    return try {
        val buffer = ByteArray(sampleSize)
        val inputStream = file.inputStream()
        val bytesRead = inputStream.read(buffer)
        inputStream.close()

        // Check each character in the buffer
        buffer.take(bytesRead).forEach { byte ->
            if (!byte.toInt().toChar().isPrintable()) {
                return false // Immediately reject if a non-printable character is found
            }
        }
        true // If no non-printable characters are found, it's a text file
    } catch (e: Exception) {
        e.printStackTrace()
        false // Treat exceptions as non-printable (e.g., file read errors)
    }
}

// Updated isPrintable function
fun Char.isPrintable(): Boolean {
    val codePoint = this.code
    return this in '\u0020'..'\u007E' || // Basic ASCII
            this in '\u00A0'..'\u00FF' || // Latin-1 Supplement
            this.isLetterOrDigit() || // Letters and digits from all scripts
            this.isWhitespace() || // Space, tab, etc.
            (codePoint in 0x1F300..0x1F5FF) || // Miscellaneous Symbols and Pictographs
            (codePoint in 0x1F600..0x1F64F) || // Emoticons
            (codePoint in 0x1F680..0x1F6FF) || // Transport and Map Symbols
            (codePoint in 0x2600..0x26FF) || // Miscellaneous Symbols
            (codePoint in 0x2700..0x27BF) // Dingbats
}

