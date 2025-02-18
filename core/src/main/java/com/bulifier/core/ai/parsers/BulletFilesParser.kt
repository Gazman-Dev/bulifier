package com.bulifier.core.ai.parsers

object BulletFilesParser {

    fun parse(response: String): List<ParsedFileContent> {
        // First, normalize markdown: remove CRLF/CR and markdown styling.
        val plainText = normalizeMarkdown(response)
        val lines = plainText.lines().filter { it.isNotBlank() && it.trim() != "---" }
        val fileDataList = mutableListOf<ParsedFileContent>()

        var currentFileName = ""
        var currentFileContent = StringBuilder()
        var currentImports = mutableListOf<String>()
        var inImportsSection = false
        var lastCandidate = "" // tracks the last non-empty, non-header line (potential file name)

        for (line in lines) {
            val trimmedLine = line.trim()
            when {
                // Explicit FileName line.
                trimmedLine.startsWith("FileName:") -> {
                    if (currentFileName.isNotEmpty()) {
                        addFile(currentFileName, fileDataList, currentFileContent, currentImports)
                        currentFileContent = StringBuilder()
                        currentImports = mutableListOf()
                        inImportsSection = false
                    }
                    currentFileName =
                        normalizeFileName(trimmedLine.substringAfter("FileName:").trim())
                    lastCandidate = currentFileName
                }

                // Section header (e.g., Purpose, Imports, Definitions, etc.)
                trimmedLine.startsWith("- ") && trimmedLine.contains(":") -> {
                    val sectionName = trimmedLine.substringAfter("-")
                        .substringBefore(":")
                        .trim()
                    // When hitting a Purpose section, if no explicit file name was set
                    // or the candidate differs from the current file name, finalize the previous file.
                    if (sectionName.equals("Purpose", ignoreCase = true)) {
                        if (currentFileName.isEmpty() || normalizeFileName(lastCandidate) != currentFileName) {
                            if (currentFileName.isNotEmpty()) {
                                addFile(
                                    currentFileName,
                                    fileDataList,
                                    currentFileContent,
                                    currentImports
                                )
                                currentFileContent = StringBuilder()
                                currentImports = mutableListOf()
                                inImportsSection = false
                            }
                            if (lastCandidate.isNotBlank()) {
                                currentFileName = normalizeFileName(lastCandidate)
                            }
                        }
                    }
                    inImportsSection = sectionName.equals("Imports", ignoreCase = true)
                    if (!inImportsSection) {
                        currentFileContent.append(line).append("\n")
                    }
                }

                // Lines within the Imports section.
                inImportsSection && trimmedLine.startsWith("- ") -> {
                    val importLine = trimmedLine.substringAfter("- ").trim()
                    if (!importLine.startsWith("None", ignoreCase = true)) {
                        val importStatement = importLine.removePrefix("import").trim()
                        if (importStatement.isNotEmpty()) {
                            currentImports.add(importStatement)
                        }
                    }
                }

                // Any other line is added to the file content.
                else -> {
                    currentFileContent.append(line).append("\n")
                    // If the line is non-header, update the candidate for file name.
                    if (!trimmedLine.startsWith("-")) {
                        lastCandidate = trimmedLine
                    }
                }
            }
        }

        // Finalize the last parsed file data.
        if (currentFileName.isNotEmpty()) {
            addFile(currentFileName, fileDataList, currentFileContent, currentImports)
        }

        return fileDataList
    }

    // Replaces CRLF and CR with LF, and removes markdown bold and code formatting markers.
    private fun normalizeMarkdown(input: String): String {
        return input
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("**", "")
            .replace("`", "")
    }

    // Normalizes the file name by removing leading characters that are not letters or underscores.
    private fun normalizeFileName(fileName: String): String {
        return fileName.replace(Regex("^[^a-zA-Z_]+"), "")
    }

    private fun addFile(
        currentFileName: String,
        fileDataList: MutableList<ParsedFileContent>,
        currentFileContent: StringBuilder,
        currentImports: MutableList<String>
    ) {
        val content = trimNonEnglishLines(currentFileContent.toString())
            // Remove any leftover markdown styling if needed.
            .replace("""[*`].+""".toRegex(), "")
            .let {
                if (currentImports.isNotEmpty()) {
                    // Prepend the imports as valid import statements at the head.
                    currentImports.joinToString("\n") { "import $it" } + "\n\n" + it
                } else {
                    it
                }
            }.trim() // Extra trim in case of empty content

        fileDataList.add(
            ParsedFileContent(
                fullPath = "$currentFileName.bul",
                content = content
            )
        )
    }

    private fun trimNonEnglishLines(fileContent: String): String {
        val englishRegex = Regex("[a-zA-Z]")
        val lines = fileContent.lines()

        val startIndex = lines.indexOfFirst { it.contains(englishRegex) }
        val endIndex = lines.indexOfLast { it.contains(englishRegex) }

        return when {
            startIndex != -1 && endIndex != -1 -> lines.subList(startIndex, endIndex + 1)
                .joinToString("\n")

            startIndex != -1 -> lines.subList(startIndex, lines.size).joinToString("\n")
            endIndex != -1 -> lines.subList(0, endIndex + 1).joinToString("\n")
            else -> "" // Return an empty string if no lines contain English characters
        }
    }
}
