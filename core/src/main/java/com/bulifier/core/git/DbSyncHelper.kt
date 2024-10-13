import android.content.Context
import com.bulifier.core.db.Content
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
        val srcDir = File(projectDir, "src")
        val schemasDir = File(projectDir, "schemas")

        db.fileDao().deleteFilesByProjectId(projectId)

        // Process the src directory with an empty path
        processDirectory(srcDir, "", projectId)

        // Process the schemas directory with path "schemas"
        processDirectory(schemasDir, "schemas", projectId)
    }

    private suspend fun processDirectory(currentDir: File, parentPath: String, projectId: Long) {
        if (!currentDir.exists()) return

        val schemasRoot = DbFile(
            projectId = projectId,
            path = "",
            fileName = "schemas",
            isFile = false
        )
        db.fileDao().insertFile(schemasRoot)

        currentDir.listFiles()?.forEach { file ->
            val fileName = file.name
            val isFile = file.isFile
            val size = if (isFile) file.length().toInt() else 0
            val path = parentPath

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
                // Read the file content and insert the Content entity
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
        val srcDir = context.filesDir.createOverrideEmptyDirectory("$projectName/src", clearOldFiles)
        val schemasDir = context.filesDir.createOverrideEmptyDirectory("$projectName/schemas", clearOldFiles)

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

    private fun File.createOverrideEmptyDirectory(folderName: String, clearOldFiles: Boolean) =
        File(this, folderName).apply {
            mkdirs()
            if(clearOldFiles) {
                deleteAllFilesInFolder(this)
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
