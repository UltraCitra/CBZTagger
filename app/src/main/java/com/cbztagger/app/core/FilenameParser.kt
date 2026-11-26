package com.cbztagger.app.core

data class ParsedFilename(
    val series: String = "",
    val number: String = "",
    val volume: Int? = null,
    val year: Int? = null,
    val title: String = "",
)

/**
 * Offline filename parser supporting both Western comic naming conventions
 * (e.g. "Batman v02 #012 (2023).cbz") and Korean/Japanese volume/chapter conventions
 * (e.g. "원피스 012권 (2020).cbz", "시리즈 제3권.cbz", "작품명 05화.cbz").
 */
object FilenameParser {
    private val YEAR_REGEX = Regex("""[\(\[]((?:19|20)\d{2})[\)\]]""")
    private val PAREN_OR_BRACKET_REGEX = Regex("""\([^)]*\)|\[[^\]]*\]""")
    private val VOLUME_REGEX = Regex(
        """(?i)(?:^|[\s._-])(?:vol\.?|volume|v)\s*0*(\d+)(?=$|[\s._#-])""",
    )
    private val KOREAN_ISSUE_REGEX = Regex(
        """(?:제\s*)?0*(\d+(?:\.\d+)?)\s*(권|화|부|편)(?=$|[\s._-])""",
    )
    private val HASH_ISSUE_REGEX = Regex(
        """#0*(\d+(?:\.\d+)?[a-zA-Z]?)(?=$|[\s._-])""",
    )
    private val CHAPTER_ISSUE_REGEX = Regex(
        """(?i)(?:^|[\s._-])(?:ch\.?|chapter|ep\.?|issue|no\.?)\s*0*(\d+(?:\.\d+)?)(?=$|[\s._-])""",
    )
    private val TRAILING_NUMBER_REGEX = Regex(
        """(?:^|[\s._-])0*(\d+(?:\.\d+)?)$""",
    )

    fun parse(filename: String): ParsedFilename {
        val base = filename
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("(?i)\\.(cbz|zip)$"), "")
            .replace('_', ' ')
            .trim()

        if (base.isEmpty()) return ParsedFilename()

        // 1. Extract year from (YYYY) or [YYYY]
        val yearMatch = YEAR_REGEX.find(base)
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()

        // Remove parenthesized/bracketed metadata like (Digital), [Scan], (2024)
        var working = base.replace(PAREN_OR_BRACKET_REGEX, " ").replace(Regex("\\s+"), " ").trim()

        // 2. Extract volume (v02, Vol.3, Volume 1)
        var volume: Int? = null
        val volMatch = VOLUME_REGEX.find(working)
        if (volMatch != null) {
            volume = volMatch.groupValues[1].toIntOrNull()
            working = working.removeRange(volMatch.range).replace(Regex("\\s+"), " ").trim()
        }

        // 3. Extract issue/volume number + optional trailing title
        var number = ""
        var title = ""

        val koreanMatch = KOREAN_ISSUE_REGEX.find(working)
        val hashMatch = HASH_ISSUE_REGEX.find(working)
        val chapterMatch = CHAPTER_ISSUE_REGEX.find(working)

        val primaryMatch = listOfNotNull(hashMatch, koreanMatch, chapterMatch)
            .minByOrNull { it.range.first }

        if (primaryMatch != null) {
            number = primaryMatch.groupValues[1]
            val before = working.substring(0, primaryMatch.range.first).trim(' ', '-', '.', '_')
            val after = working.substring(primaryMatch.range.last + 1).trim(' ', '-', '.', '_')
            working = before
            title = after
        } else {
            // Check "Series - 005 - Title" pattern
            val dashParts = working.split(Regex("\\s+-\\s+"))
            if (dashParts.size >= 2) {
                val numCandidate = dashParts[1].trim().removePrefix("#").trimStart('0').ifEmpty { "0" }
                if (Regex("^\\d+(?:\\.\\d+)?$").matches(numCandidate)) {
                    working = dashParts[0].trim()
                    number = numCandidate
                    if (dashParts.size >= 3) {
                        title = dashParts.drop(2).joinToString(" - ").trim()
                    }
                }
            }
            if (number.isEmpty()) {
                val trailingMatch = TRAILING_NUMBER_REGEX.find(working)
                if (trailingMatch != null) {
                    number = trailingMatch.groupValues[1]
                    working = working.substring(0, trailingMatch.range.first).trim(' ', '-', '.', '_')
                }
            }
        }

        val series = working.replace(Regex("\\s+"), " ").trim(' ', '-', '.', '_')
        return ParsedFilename(
            series = series,
            number = number,
            volume = volume,
            year = year,
            title = title,
        )
    }

    fun applyToComicInfo(existing: ComicInfo, filename: String, overwriteExisting: Boolean = false): ComicInfo {
        val parsed = parse(filename)
        return existing.copy(
            series = if (overwriteExisting || existing.series.isBlank()) parsed.series.ifBlank { existing.series } else existing.series,
            number = if (overwriteExisting || existing.number.isBlank()) parsed.number.ifBlank { existing.number } else existing.number,
            volume = if (overwriteExisting || existing.volume == null) parsed.volume ?: existing.volume else existing.volume,
            year = if (overwriteExisting || existing.year == null) parsed.year ?: existing.year else existing.year,
            title = if (overwriteExisting || existing.title.isBlank()) parsed.title.ifBlank { existing.title } else existing.title,
        )
    }
}
