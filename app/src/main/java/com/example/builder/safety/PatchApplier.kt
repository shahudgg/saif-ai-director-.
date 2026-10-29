package com.example.builder.safety

import java.util.regex.Pattern

data class FailedHunk(
    val index: Int,
    val searchBlock: String,
    val replaceBlock: String,
    val reason: String
)

data class PatchResult(
    val success: Boolean,
    val updatedText: String,
    val appliedHunks: Int,
    val failedHunks: List<FailedHunk>
)

/**
 * Applies search/replace patch hunks to target files with:
 * 1. Exact matching
 * 2. Whitespace-tolerant line matching
 * 3. Indentation-insensitive match
 */
object PatchApplier {

    private val HUNK_PATTERN = Pattern.compile(
        """<<<<<<<\s*SEARCH\r?\n([\s\S]*?)\r?\n=======\r?\n([\s\S]*?)\r?\n>>>>>>>\s*(?:REPLACE)?""",
        Pattern.CASE_INSENSITIVE
    )

    fun apply(originalText: String, patchContent: String): PatchResult {
        if (patchContent.isBlank()) {
            return PatchResult(success = true, updatedText = originalText, appliedHunks = 0, failedHunks = emptyList())
        }

        val matcher = HUNK_PATTERN.matcher(patchContent)
        var currentText = originalText
        var applied = 0
        val failed = mutableListOf<FailedHunk>()
        var hunkIndex = 0

        while (matcher.find()) {
            hunkIndex++
            val searchBlock = matcher.group(1) ?: ""
            val replaceBlock = matcher.group(2) ?: ""

            if (searchBlock.isEmpty()) {
                failed.add(FailedHunk(hunkIndex, searchBlock, replaceBlock, "Empty SEARCH block"))
                continue
            }

            // Strategy 1: Exact substring match
            if (currentText.contains(searchBlock)) {
                currentText = currentText.replaceFirst(searchBlock, replaceBlock)
                applied++
                continue
            }

            // Strategy 2: Line-by-line whitespace-trimmed match
            val whitespaceMatch = findWhitespaceTolerantMatch(currentText, searchBlock)
            if (whitespaceMatch != null) {
                currentText = currentText.replaceRange(whitespaceMatch.first, whitespaceMatch.second, replaceBlock)
                applied++
                continue
            }

            // Strategy 3: Relaxed trimmed-lines match
            val relaxedMatch = findRelaxedMatch(currentText, searchBlock)
            if (relaxedMatch != null) {
                currentText = currentText.replaceRange(relaxedMatch.first, relaxedMatch.second, replaceBlock)
                applied++
                continue
            }

            // Hunk failed to find a match
            failed.add(FailedHunk(hunkIndex, searchBlock, replaceBlock, "SEARCH block not found in target"))
        }

        return PatchResult(
            success = failed.isEmpty(),
            updatedText = currentText,
            appliedHunks = applied,
            failedHunks = failed
        )
    }

    private fun findWhitespaceTolerantMatch(source: String, search: String): Pair<Int, Int>? {
        val searchLines = search.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (searchLines.isEmpty()) return null

        val sourceLines = source.lines()
        for (i in 0..sourceLines.size - searchLines.size) {
            var matched = true
            for (j in searchLines.indices) {
                if (sourceLines[i + j].trim() != searchLines[j]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                // Calculate char indices in source
                var startIdx = 0
                for (k in 0 until i) {
                    startIdx += sourceLines[k].length + 1
                }
                var endIdx = startIdx
                for (k in 0 until searchLines.size) {
                    endIdx += sourceLines[i + k].length + 1
                }
                return Pair(startIdx.coerceAtMost(source.length), endIdx.coerceAtMost(source.length))
            }
        }
        return null
    }

    private fun findRelaxedMatch(source: String, search: String): Pair<Int, Int>? {
        val cleanSearch = search.replace(Regex("""\s+"""), " ").trim()
        val cleanSource = source.replace(Regex("""\s+"""), " ")
        val idx = cleanSource.indexOf(cleanSearch)
        if (idx == -1) return null

        // If unique in normalized space, map to approximate line bounds
        val firstLine = search.lines().firstOrNull { it.trim().isNotEmpty() }?.trim() ?: return null
        val lastLine = search.lines().lastOrNull { it.trim().isNotEmpty() }?.trim() ?: return null

        val startPos = source.indexOf(firstLine)
        if (startPos == -1) return null
        val endPos = source.indexOf(lastLine, startPos)
        if (endPos == -1) return null

        return Pair(startPos, (endPos + lastLine.length).coerceAtMost(source.length))
    }
}
