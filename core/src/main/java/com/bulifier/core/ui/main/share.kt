package com.bulifier.core.ui.main

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.bulifier.core.db.FileData
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MultiFileSharingUtil(
    private val context: Context,
    private val authority: String = context.applicationContext.packageName + ".fileprovider"
) {
    fun shareFiles(contents: List<FileData>) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            val zipFile = createZipFile(contents)
            val uri = FileProvider.getUriForFile(context, authority, zipFile)

            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            context.startActivity(Intent.createChooser(shareIntent, "Share files using").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: IOException) {
            e.printStackTrace()
            // Handle the exception (e.g., show an error message to the user)
        }
    }

    private fun createZipFile(contents: List<FileData>): File {
        val zipFile = File(context.cacheDir, "src.zip")
        deleteFileOrFolder(zipFile)

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for (content in contents) {
                val entry = ZipEntry(content.path + "/" + content.fileName)
                zos.putNextEntry(entry)
                zos.write(content.content.toByteArray())
                zos.closeEntry()
            }
        }
        return zipFile
    }

    private fun deleteFileOrFolder(file: File) {
        if (!file.exists()) {
            return
        }
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteFileOrFolder(child)
                }
            }
        }
        file.delete()
    }
}