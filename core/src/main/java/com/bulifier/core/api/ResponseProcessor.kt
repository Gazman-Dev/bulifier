package com.bulifier.core.api

class ResponseProcessor {

    fun parseResponse(response: String, basePath: String): List<FileData> {
        val lines = response.lines()
        val fileDataList = mutableListOf<FileData>()

        var currentFileName = ""
        var currentFileContent = StringBuilder()
        var currentImports = mutableListOf<String>()
        var inImportsSection = false

        for (line in lines) {
            val trimmedLine = line.trim()
            when {
                trimmedLine.startsWith("FileName:") -> {
                    // Save the current file data if it exists
                    if (currentFileName.isNotEmpty()) {
                        addFile(
                            basePath,
                            currentFileName,
                            fileDataList,
                            currentFileContent,
                            currentImports
                        )
                        // Reset variables for the next file
                        currentFileContent = StringBuilder()
                        currentImports = mutableListOf()
                        inImportsSection = false
                    }
                    currentFileName = trimmedLine.substringAfter("FileName:").trim()
                }

                trimmedLine.startsWith("-") && trimmedLine.contains(":") -> {
                    // Start of a new section
                    val sectionName = trimmedLine.substringAfter("-").substringBefore(":").trim()
                    inImportsSection = sectionName == "Imports"
                    if (!inImportsSection) {
                        currentFileContent.append(line + "\n")
                    }
                }

                inImportsSection && trimmedLine.startsWith("-") -> {
                    // Line within the Imports section
                    val importLine = trimmedLine.substringAfter("-").trim()
                    val importStatement = importLine.removePrefix("import").trim()
                    currentImports.add("$basePath/$importStatement")
                }

                else -> {
                    // Regular content line
                    currentFileContent.append(line + "\n")
                }
            }
        }

        // Add the last parsed file data
        if (currentFileName.isNotEmpty()) {
            addFile(
                basePath,
                currentFileName,
                fileDataList,
                currentFileContent,
                currentImports
            )
        }

        return fileDataList
    }

    private fun addFile(
        basePath: String,
        currentFileName: String,
        fileDataList: MutableList<FileData>,
        currentFileContent: java.lang.StringBuilder,
        currentImports: MutableList<String>
    ) {
        val fullPath = "$basePath/$currentFileName"
        fileDataList.add(
            FileData(
                content = currentFileContent.toString().trim(),
                imports = currentImports,
                fullPath = fullPath,
                isFile = true
            )
        )
    }
}

data class FileData(
    val content: String,
    val imports: List<String>,
    val fullPath: String,
    val isFile: Boolean
)
