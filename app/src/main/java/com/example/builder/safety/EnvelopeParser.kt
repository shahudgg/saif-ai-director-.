package com.example.builder.safety

import java.util.regex.Pattern

sealed class EnvelopeItem {
    data class FileEnvelope(val path: String, val content: String, val isTruncated: Boolean = false) : EnvelopeItem()
    data class PatchEnvelope(val path: String, val patchContent: String, val isTruncated: Boolean = false) : EnvelopeItem()
}

data class EnvelopeParseResult(
    val items: List<EnvelopeItem>,
    val ignoredProseCount: Int,
    val hasTruncatedEnvelope: Boolean
)

/**
 * Robust parser that extracts code ONLY from <file path="...">...</file>
 * and <patch path="...">...</patch> envelopes.
 * Completely ignores everything outside envelopes, discarding leaked reasoning and chain-of-thought.
 */
object EnvelopeParser {

    private val FILE_TAG_PATTERN = Pattern.compile(
        """<file\s+path=["']([^"']+)["']\s*>([\s\S]*?)(?:</file>|$)""",
        Pattern.CASE_INSENSITIVE
    )

    private val PATCH_TAG_PATTERN = Pattern.compile(
        """<patch\s+path=["']([^"']+)["']\s*>([\s\S]*?)(?:</patch>|$)""",
        Pattern.CASE_INSENSITIVE
    )

    fun parse(rawOutput: String): EnvelopeParseResult {
        if (rawOutput.isBlank()) {
            return EnvelopeParseResult(emptyList(), 0, false)
        }

        val items = mutableListOf<EnvelopeItem>()
        var hasTruncated = false

        // 1. Match <file path="...">...</file>
        val fileMatcher = FILE_TAG_PATTERN.matcher(rawOutput)
        while (fileMatcher.find()) {
            val path = fileMatcher.group(1)?.trim() ?: ""
            val fullMatch = fileMatcher.group(0) ?: ""
            val content = fileMatcher.group(2) ?: ""
            val isClosed = fullMatch.contains("</file>", ignoreCase = true)
            if (!isClosed) hasTruncated = true

            if (path.isNotBlank()) {
                items.add(EnvelopeItem.FileEnvelope(path, content.trim(), !isClosed))
            }
        }

        // 2. Match <patch path="...">...</patch>
        val patchMatcher = PATCH_TAG_PATTERN.matcher(rawOutput)
        while (patchMatcher.find()) {
            val path = patchMatcher.group(1)?.trim() ?: ""
            val fullMatch = patchMatcher.group(0) ?: ""
            val content = patchMatcher.group(2) ?: ""
            val isClosed = fullMatch.contains("</patch>", ignoreCase = true)
            if (!isClosed) hasTruncated = true

            if (path.isNotBlank()) {
                items.add(EnvelopeItem.PatchEnvelope(path, content.trim(), !isClosed))
            }
        }

        // Calculate approximate prose outside envelopes
        var totalEnvelopeChars = 0
        for (item in items) {
            when (item) {
                is EnvelopeItem.FileEnvelope -> totalEnvelopeChars += item.content.length + 30
                is EnvelopeItem.PatchEnvelope -> totalEnvelopeChars += item.patchContent.length + 30
            }
        }
        val ignoredProseChars = (rawOutput.length - totalEnvelopeChars).coerceAtLeast(0)

        return EnvelopeParseResult(
            items = items,
            ignoredProseCount = ignoredProseChars,
            hasTruncatedEnvelope = hasTruncated
        )
    }
}
