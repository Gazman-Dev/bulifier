package com.bulifier.core.ai.parsers

object RawFilesParser {

    fun parse(input: String): List<ParsedFileContent> {
        val segments = input.split("```")
        val parsedFiles = mutableListOf<ParsedFileContent>()

        // Iterate over segments. Code blocks should be at odd indices.
        for (i in segments.indices) {
            if (i % 2 == 1) { // This is a code block.
                val codeBlock = segments[i]
                val codeLines = codeBlock.lines()
                // Remove the first line (language specifier or blank)
                val content = if (codeLines.size > 1) {
                    codeLines.drop(1).joinToString("\n").trimEnd() + "\n"
                } else {
                    ""
                }

                // The preceding segment (metadata) to extract the file name.
                val headerSegment = segments[i - 1]
                // Find the last line that contains '#' (assuming it marks the file header)
                val headerLine = headerSegment.lines().lastOrNull { it.contains("#") }
                if (headerLine != null) {
                    // Remove '#' characters and trim spaces.
                    val headerContent = headerLine.replace("#", "").trim()
                    // Remove a trailing colon if present.
                    val headerWithoutColon = if (headerContent.endsWith(":")) {
                        headerContent.dropLast(1).trim()
                    } else {
                        headerContent
                    }

                    // Tokenize the header by splitting on any non-alphanumeric, non-dot, non-slash, non-backslash characters.
                    val tokens = headerWithoutColon.split(Regex("[^a-zA-Z0-9./\\\\]+"))
                        .filter { it.isNotBlank() }

                    // If no tokens are found, fall back to the entire header.
                    val fileName = if (tokens.isEmpty()) {
                        headerWithoutColon
                    } else {
                        // Scoring function: 1 point for a dot, 1 point for a slash (either / or \)
                        fun scoreToken(token: String): Int {
                            var score = 0
                            if (token.contains('.')) score++
                            if (token.contains('/') || token.contains('\\')) score++
                            return score
                        }

                        val scores = tokens.map { scoreToken(it) }
                        // First, pick the index of the first maximum score.
                        val maxScore = scores.maxOrNull() ?: 0
                        val bestCandidateIndex = scores.indexOf(maxScore)

                        // Among tokens with the maximum score, pick the one closest to the initially winning index.
                        val candidateIndices =
                            scores.withIndex().filter { it.value == maxScore }.map { it.index }
                        val chosenIndex =
                            candidateIndices.minByOrNull { Math.abs(it - bestCandidateIndex) }
                                ?: bestCandidateIndex
                        tokens[chosenIndex]
                    }

                    parsedFiles.add(
                        ParsedFileContent(
                            fileName.trim()
                                .removePrefix("/"), content
                        )
                    )
                }
            }
        }
        return parsedFiles
    }
}