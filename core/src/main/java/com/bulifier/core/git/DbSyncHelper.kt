import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.bulifier.core.db.Content
import com.bulifier.core.db.db
import com.bulifier.core.db.getBinaryFile
import java.io.File
import java.io.InputStream
import com.bulifier.core.db.File as DbFile

private const val MAX_FILE_SIZE = 1024 * 40

class DbSyncHelper(
    private val context: Context
) {

    private val db = context.db

    suspend fun importProject(projectId: Long) {
        // Fetch the project name from the database
        val projectName = db.fileDao().getProjectById(projectId).projectName

        // Define the project directory
        val projectDir = File(context.filesDir, projectName)


        File(context.filesDir, "binary/$projectId").deleteRecursively()
        db.fileDao().deleteFilesByProjectId(projectId)


        processDirectory(projectDir, "", projectId)
    }


    private suspend fun processDirectory(currentDir: File, parentPath: String, projectId: Long) {
        if (!currentDir.exists()) return

        currentDir.listFiles()?.forEach { file ->
            val fileName = file.name
            val isFile = file.isFile
            val size = if (isFile) file.length() else 0
            val path = parentPath

            if (fileName.lowercase().trim() == ".git") {
                return@forEach
            }
            val isBinary = isFile && !isTextFile(file)

            // Create and insert the File entity
            val fileEntity = DbFile(
                projectId = projectId,
                path = path,
                fileName = fileName,
                isFile = isFile,
                size = size,
                isBinary = isBinary
            )
            val fileId = db.fileDao().insertFile(fileEntity)

            if (isFile) {
                if (isBinary) {
                    file.copyTo(getBinaryFile(context, fileId, projectId), overwrite = true)
                } else {
                    val content = file.readText()
                    val contentEntity = Content(
                        fileId = fileId,
                        content = content
                    )
                    db.fileDao().insertContent(contentEntity)
                }
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

        exportProject(projectId, srcDir, clearOldFiles)
    }

    suspend fun exportProject(
        projectId: Long,
        destination: File,
        clearOldFiles: Boolean,
        extensionsBlackList: List<String> = emptyList(),
    ) {
        if (clearOldFiles) {
            deleteFilesExceptGit(destination)
        }

        db.fileDao().exportFilesAndContents(projectId, extensionsBlackList).forEach { fileData ->
            exportFile(
                baseDir = destination,
                relativePath = fileData.path,
                fileName = fileData.fileName,
                content = fileData.content,
                isFile = fileData.isFile
            )
        }

        db.fileDao().exportBinaryFiles(projectId).forEach {
            val fileDestination = if (it.path.isEmpty()) {
                it.fileName
            } else {
                "${it.path}/${it.fileName}"
            }
            val binaryFile = it.getBinaryFile(context)
            try {
                binaryFile.copyTo(File(destination, fileDestination), overwrite = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun deleteFilesExceptGit(directory: File) {
        if (!directory.exists() || !directory.isDirectory) return

        directory.listFiles()?.forEach { file ->
            if (file.name != ".git") {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }
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

fun isTextFile(file: File): Boolean {
    val length = file.length()
    if (length == 0L) {
        return true
    }
    return length < MAX_FILE_SIZE && file.inputStream()
        .use { inputStream -> isTextStream(inputStream) }
}

fun AssetManager.isTextAsset(fileName: String): Boolean {
    return open(fileName).use { inputStream ->
        inputStream.available() == 0 || isTextStream(inputStream)
    }
}

private fun isTextStream(inputStream: InputStream, sampleSize: Int = 1024): Boolean {
    return try {
        val buffer = ByteArray(sampleSize)
        val bytesRead = inputStream.read(buffer)

        // Check each character in the buffer
        buffer.take(bytesRead).all { byte -> byte.toInt().toChar().isPrintable() }
    } catch (e: Exception) {
        e.printStackTrace()
        false // Handle file read errors
    }
}

// Updated isPrintable function
fun Char.isPrintable(): Boolean {
    val codePoint = this.code
    val printable = this in '\u0020'..'\u007E' || // Basic ASCII
            this in '\u00A0'..'\u00FF' || // Latin-1 Supplement
            this in '\u27EA'..'\u27EB' || // Allow ⟪ and ⟫
            this in '\u2018'..'\u201F' || // Typographic quotation marks (‘’ “” „)
            this == '\u2013' || this == '\u2014' || // En dash (–) & Em dash (—)
            this.isLetterOrDigit() || // Letters and digits from all scripts
            this.isWhitespace() || // Space, tab, etc.
            codePoint == 65506 || // Allow full-width tilde
            (codePoint in 0x1F300..0x1F5FF) || // Miscellaneous Symbols and Pictographs
            (codePoint in 0x1F600..0x1F64F) || // Emoticons
            (codePoint in 0x1F680..0x1F6FF) || // Transport and Map Symbols
            (codePoint in 0x2600..0x26FF) || // Miscellaneous Symbols
            (codePoint in 0x2700..0x27BF)
    if (!printable) {
        Log.d("isPrintable", "Non-printable character: $this ($codePoint)")
    }
    return printable // Dingbats
}

