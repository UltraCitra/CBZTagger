package com.cbztagger.app.core

enum class PageType(val xmlValue: String, val labelKo: String) {
    FRONT_COVER("FrontCover", "앞표지 (FrontCover)"),
    INNER_COVER("InnerCover", "속표지 (InnerCover)"),
    ROUNDUP("Roundup", "요약 (Roundup)"),
    STORY("Story", "본문 (Story)"),
    ADVERTISEMENT("Advertisement", "광고 (Advertisement)"),
    EDITORIAL("Editorial", "편집 (Editorial)"),
    LETTERS("Letters", "독자란 (Letters)"),
    PREVIEW("Preview", "미리보기 (Preview)"),
    BACK_COVER("BackCover", "뒷표지 (BackCover)"),
    OTHER("Other", "기타 (Other)"),
    DELETED("Deleted", "삭제됨 (Deleted)"),
    ;

    companion object {
        fun fromXml(value: String?): String {
            if (value.isNullOrBlank()) return ""
            return entries.firstOrNull { it.xmlValue.equals(value.trim(), ignoreCase = true) }?.xmlValue
                ?: value.trim()
        }
    }
}

data class PageInfo(
    val image: Int,
    val type: String = "",
    val doublePage: Boolean? = null,
    val imageSize: Long? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val bookmark: String = "",
)

data class ComicInfo(
    val series: String = "",
    val number: String = "",
    val count: Int? = null,
    val title: String = "",
    val volume: Int? = null,
    val alternateSeries: String = "",
    val alternateNumber: String = "",
    val alternateCount: Int? = null,
    val summary: String = "",
    val notes: String = "",
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
    val writer: String = "",
    val penciller: String = "",
    val inker: String = "",
    val colorist: String = "",
    val letterer: String = "",
    val coverArtist: String = "",
    val editor: String = "",
    val publisher: String = "",
    val imprint: String = "",
    val genre: String = "",
    val web: String = "",
    val pageCount: Int? = null,
    val languageIso: String = "",
    val format: String = "",
    val blackAndWhite: Boolean? = null,
    val manga: String = "",
    val characters: String = "",
    val teams: String = "",
    val locations: String = "",
    val scanInformation: String = "",
    val storyArc: String = "",
    val seriesGroup: String = "",
    val ageRating: String = "",
    val communityRating: Float? = null,
    val pages: List<PageInfo> = emptyList(),
) {
    val isEmpty: Boolean
        get() =
            series.isBlank() &&
                number.isBlank() &&
                title.isBlank() &&
                publisher.isBlank() &&
                summary.isBlank() &&
                writer.isBlank() &&
                penciller.isBlank() &&
                genre.isBlank() &&
                year == null &&
                volume == null &&
                pages.isEmpty()

    /**
     * Formats author credits prioritizing Penciller (그림작가) over Writer (글작가).
     * - Same author for both: shown once.
     * - Different authors: "그림작가 | 글작가".
     */
    fun formatAuthorsDisplay(): String {
        val p = penciller.trim()
        val w = writer.trim()
        return when {
            p.isNotBlank() && w.isNotBlank() -> {
                if (p.equals(w, ignoreCase = true)) p else "$p | $w"
            }
            p.isNotBlank() -> p
            w.isNotBlank() -> w
            else -> ""
        }
    }

    /**
     * Returns true when metadata has enough identifying info to be shown above the filename
     * on the main library card: either (Series + Number) or Title.
     * Author-only or Series without Number (and without Title) does not qualify.
     */
    fun hasLibraryHeadlineMeta(): Boolean =
        (series.isNotBlank() && number.isNotBlank()) || title.isNotBlank()

    /**
     * Primary library metadata: "{series} #{number} ({year})", "{title} ({year})",
     * or "{series} ({year})" as fallback when neither number nor title is set.
     */
    fun formatLibraryPrimaryMeta(): String = buildString {
        when {
            series.isNotBlank() && number.isNotBlank() -> {
                append(series.trim())
                append(" #${number.trim()}")
                if (year != null) append(" ($year)")
            }
            title.isNotBlank() -> {
                append(title.trim())
                if (year != null) append(" ($year)")
            }
            series.isNotBlank() -> {
                append(series.trim())
                if (year != null) append(" ($year)")
            }
        }
    }

    /**
     * Single inline library metadata line with unified style:
     * "{series} #{issue} | {penciller}" (or "{title} | {penciller} | {writer}" when different).
     */
    fun formatLibraryMetaLine(emptyFallback: String = "메타데이터 없음"): String {
        val primary = formatLibraryPrimaryMeta()
        val authors = formatAuthorsDisplay()
        return when {
            primary.isNotBlank() && authors.isNotBlank() -> "$primary | $authors"
            primary.isNotBlank() -> primary
            authors.isNotBlank() -> authors
            else -> emptyFallback
        }
    }

    /**
     * Resolves the (headline, subline) pair for a library CBZ card:
     * - When sufficient metadata exists ((Series + Number) or Title):
     *   headline = metadata line, subline = [displayName]
     * - Otherwise (including author-only, series-without-number, or empty):
     *   headline = [displayName], subline = metadata line (or [emptyFallback])
     */
    fun formatLibraryCardLines(
        displayName: String,
        emptyFallback: String = "메타데이터 없음",
    ): Pair<String, String> {
        val metaLine = formatLibraryMetaLine(emptyFallback)
        return if (hasLibraryHeadlineMeta()) {
            metaLine to displayName
        } else {
            displayName to metaLine
        }
    }

    fun applyBatchPatch(patch: BatchTagPatch): ComicInfo = copy(
        series = patch.series?.takeIf { it.isNotBlank() } ?: series,
        publisher = patch.publisher?.takeIf { it.isNotBlank() } ?: publisher,
        imprint = patch.imprint?.takeIf { it.isNotBlank() } ?: imprint,
        genre = patch.genre?.takeIf { it.isNotBlank() } ?: genre,
        writer = patch.writer?.takeIf { it.isNotBlank() } ?: writer,
        penciller = patch.penciller?.takeIf { it.isNotBlank() } ?: penciller,
        languageIso = patch.languageIso?.takeIf { it.isNotBlank() } ?: languageIso,
        manga = patch.manga?.takeIf { it.isNotBlank() } ?: manga,
        year = patch.year ?: year,
        volume = patch.volume ?: volume,
        count = patch.count ?: count,
        notes = patch.notes?.takeIf { it.isNotBlank() } ?: notes,
    )
}

data class BatchTagPatch(
    val series: String? = null,
    val publisher: String? = null,
    val imprint: String? = null,
    val genre: String? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val languageIso: String? = null,
    val manga: String? = null,
    val year: Int? = null,
    val volume: Int? = null,
    val count: Int? = null,
    val notes: String? = null,
) {
    val hasChanges: Boolean
        get() = listOf(
            series,
            publisher,
            imprint,
            genre,
            writer,
            penciller,
            languageIso,
            manga,
            notes,
        ).any { !it.isNullOrBlank() } || year != null || volume != null || count != null
}

enum class ZipCompressionOption(val level: Int, val label: String, val description: String) {
    KEEP_ORIGINAL(-1, "기존 유지 (재압축 안 함)", "기존 이미지 압축 방식을 유지하고 ComicInfo.xml만 빠르게 갱신"),
    STORE(0, "0 - 무압축 (Store)", "압축 없이 저장 (만화 뷰어 로딩 속도 최상)"),
    LEVEL_1(1, "1 - 가장 빠름", "Deflate 최소 압축 (빠른 저장)"),
    LEVEL_2(2, "2", "Deflate 2단계"),
    LEVEL_3(3, "3", "Deflate 3단계"),
    LEVEL_4(4, "4", "Deflate 4단계"),
    LEVEL_5(5, "5", "Deflate 5단계"),
    LEVEL_6(6, "6 - 보통 (기본 Deflate)", "Deflate 표준 압축"),
    LEVEL_7(7, "7", "Deflate 7단계"),
    LEVEL_8(8, "8", "Deflate 8단계"),
    LEVEL_9(9, "9 - 최대 압축", "Deflate 최대 압축 (최소 용량)"),
    ;

    companion object {
        fun fromLevel(level: Int): ZipCompressionOption =
            entries.firstOrNull { it.level == level } ?: KEEP_ORIGINAL
    }
}
