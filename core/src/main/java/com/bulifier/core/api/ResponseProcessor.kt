package com.bulifier.core.api

class ResponseProcessor {

    fun parseResponse(response: String): List<FileData> {
        val lines = response.split("\n").filterNot { it.isBlank() }
        val fileDataList = mutableListOf<FileData>()

        var currentFile = ""
        var currentImports = mutableListOf<String>()
        var contentBuilder = StringBuilder()

        lines.forEach { line ->
            when {
                line.startsWith("FileName:") -> {
                    if (currentFile.isNotEmpty()) {
                        fileDataList.add(
                            FileData(
                                content = contentBuilder.toString(),
                                imports = currentImports,
                                fullPath = currentFile,
                                isFile = true
                            )
                        )
                        currentImports = mutableListOf()
                        contentBuilder = StringBuilder()
                    }
                    currentFile = line.substringAfter("FileName: ").trim()
                }

                line.startsWith("Imports:") -> {
                    currentImports.addAll(
                        line.substringAfter("Imports:").split(",").map { it.trim() })
                }

                else -> {
                    contentBuilder.append(line + "\n")
                }
            }
        }

        // Add last parsed file data
        if (currentFile.isNotEmpty()) {
            fileDataList.add(
                FileData(
                    content = contentBuilder.toString(),
                    imports = currentImports,
                    fullPath = currentFile,
                    isFile = true
                )
            )
        }

        return fileDataList
    }
}

data class FileData(
    val content: String,
    val imports: List<String>,
    val fullPath: String,
    val isFile: Boolean
)
