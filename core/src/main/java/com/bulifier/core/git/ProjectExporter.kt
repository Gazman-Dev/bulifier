import android.content.Context
import com.bulifier.core.db.AppDatabase
import com.bulifier.core.db.db
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProjectExporter(
    private val context: Context
) {

    private val db = context.db
    suspend fun exportProject(projectId: Long)  {
        // Fetch the project name from the database or pass it as a parameter
        val projectName = db.fileDao().getProjectById(projectId).projectName
        val srcDir = context.filesDir.createOverrideEmptyDirectory("$projectName/src")
        val schemasDir = context.filesDir.createOverrideEmptyDirectory("$projectName/schemas")

        db.fileDao().exportFilesAndContents(projectId).forEach { fileData ->
            val baseDir: File
            val relativePath: String

            if (fileData.path == "schemas") {
                // Files in the 'schemas' package are stored in 'project_name/schemas'
                baseDir = schemasDir
                relativePath = "" // No additional relative path under schemas
            } else {
                // All other files are stored in 'project_name/src'
                baseDir = srcDir
                relativePath = fileData.path
            }

            exportFile(
                baseDir = baseDir,
                relativePath = relativePath,
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

    private fun File.createOverrideEmptyDirectory(folderName: String) =
        File(this, folderName).apply {
            mkdirs()
            deleteAllFilesInFolder(this)
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
