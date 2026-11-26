package com.cbztagger.app.core

/**
 * Template-based CBZ filename generator.
 * Supports placeholders: {series}, {issue}, {number}, {volume}, {year}, {title}, {publisher}, {count}
 */
object FileRenamer {
    const val DEFAULT_TEMPLATE = "{series} #{issue} ({year})"

    val PRESET_TEMPLATES = listOf(
        "{series} #{issue} ({year})" to "기본: Series #001 (2024).cbz",
        "{series} {issue}권 ({year})" to "한국어 권: 시리즈 001권 (2024).cbz",
        "{series} v{volume} #{issue} ({year})" to "볼륨 포함: Series v01 #001 (2024).cbz",
        "{series} #{issue} - {title}" to "제목 포함: Series #001 - Title.cbz",
        "{series} {issue}" to "간단: Series 001.cbz",
    )

    // Android ICU-safe regexes (all brackets escaped, no variable-length lookbehinds)
    private val ILLEGAL_CHARS = Regex("""[\\/:*?"<>|]""")
    private val ISSUE_NUM_REGEX = Regex("""^(\d+)(\.\d+)?([a-zA-Z]*)$""")
    private val EMPTY_BRACKETS_REGEX = Regex("""\(\s*\)|\[\s*\]|\{\s*\}""")
    private val DANGLING_PREFIX_REGEX = Regex("""(^|\s)[#vV](?=\s|$)""")
    private val DANGLING_KWON_REGEX = Regex("""(^|\s)권(?=\s|$)""")
    private val REPEATED_DASH_REGEX = Regex("""\s*-\s*-+\s*""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")

    fun hasMetadataForTemplate(
        info: ComicInfo,
        template: String = DEFAULT_TEMPLATE,
        useTitleForOneshot: Boolean = false,
    ): Boolean {
        if (useTitleForOneshot && info.series.isBlank() && info.title.isNotBlank()) {
            return true
        }
        val tpl = template.ifBlank { DEFAULT_TEMPLATE }
        val tokenValues = listOf(
            "{series}" to info.series,
            "{issue}" to info.number,
            "{number}" to info.number,
            "{volume}" to info.volume?.toString().orEmpty(),
            "{year}" to info.year?.toString().orEmpty(),
            "{title}" to info.title,
            "{publisher}" to info.publisher,
            "{count}" to info.count?.toString().orEmpty(),
        )
        val referenced = tokenValues.filter { (token, _) -> tpl.contains(token, ignoreCase = true) }
        if (referenced.isEmpty()) return true
        return referenced.any { (_, value) -> value.isNotBlank() }
    }

    fun uniqueTargetFilename(desiredCbzName: String, existingNamesLower: Set<String>): String {
        val normalized = if (desiredCbzName.endsWith(".cbz", ignoreCase = true)) {
            desiredCbzName
        } else {
            "$desiredCbzName.cbz"
        }
        if (normalized.lowercase() !in existingNamesLower) return normalized
        val stem = normalized.dropLast(4)
        var counter = 1
        while (true) {
            val candidate = "${stem}_${counter}.cbz"
            if (candidate.lowercase() !in existingNamesLower) return candidate
            counter++
        }
    }

    fun formatFilename(
        info: ComicInfo,
        template: String = DEFAULT_TEMPLATE,
        issuePadding: Int = 3,
        smartCleanup: Boolean = true,
        useTitleForOneshot: Boolean = false,
    ): String {
        if (useTitleForOneshot && info.series.isBlank() && info.title.isNotBlank()) {
            val titleResult = sanitizeFilename(sanitizePart(info.title)).trim().ifEmpty { "untitled" }
            return if (titleResult.endsWith(".cbz", ignoreCase = true)) titleResult else "$titleResult.cbz"
        }
        val paddedIssue = padIssueNumber(info.number, issuePadding)
        val paddedVolume = info.volume?.let {
            if (issuePadding > 1) it.toString().padStart(2, '0') else it.toString()
        }.orEmpty()

        val replacements = mapOf(
            "{series}" to sanitizePart(info.series),
            "{issue}" to sanitizePart(paddedIssue),
            "{number}" to sanitizePart(paddedIssue),
            "{volume}" to sanitizePart(paddedVolume),
            "{year}" to (info.year?.toString().orEmpty()),
            "{title}" to sanitizePart(info.title),
            "{publisher}" to sanitizePart(info.publisher),
            "{count}" to (info.count?.toString().orEmpty()),
        )

        var result = template.ifBlank { DEFAULT_TEMPLATE }
        for ((token, value) in replacements) {
            result = result.replace(token, value, ignoreCase = true)
        }

        if (smartCleanup) {
            result = smartClean(result)
        }

        result = sanitizeFilename(result).trim()
        if (result.isEmpty()) {
            result = "untitled"
        }
        return if (result.endsWith(".cbz", ignoreCase = true)) result else "$result.cbz"
    }

    fun padIssueNumber(raw: String, padding: Int): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || padding <= 0) return trimmed

        val match = ISSUE_NUM_REGEX.matchEntire(trimmed) ?: return trimmed
        val intPart = match.groupValues[1].padStart(padding, '0')
        val fracPart = match.groupValues[2]
        val suffix = match.groupValues[3]
        return "$intPart$fracPart$suffix"
    }

    private fun smartClean(input: String): String {
        var s = input
        // Remove empty brackets like (), [], {}
        s = s.replace(EMPTY_BRACKETS_REGEX, "")
        // Remove dangling volume/issue prefixes like "v " or "# " or "권" with no number
        s = s.replace(DANGLING_PREFIX_REGEX, "$1")
        s = s.replace(DANGLING_KWON_REGEX, "$1")
        // Collapse repeated dashes and spaces
        s = s.replace(REPEATED_DASH_REGEX, " - ")
        s = s.replace(MULTI_SPACE_REGEX, " ")
        // Trim dangling leading/trailing dashes or spaces
        s = s.trim(' ', '-', '.', '_')
        return s
    }

    private fun sanitizePart(part: String): String =
        part.trim().replace(ILLEGAL_CHARS, " ").replace(MULTI_SPACE_REGEX, " ").trim()

    private fun sanitizeFilename(name: String): String =
        name.replace(ILLEGAL_CHARS, "_").replace(MULTI_SPACE_REGEX, " ").trim()
}
