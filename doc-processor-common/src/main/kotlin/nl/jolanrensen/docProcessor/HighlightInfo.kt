package nl.jolanrensen.docProcessor

data class HighlightInfo(
    val range: IntRange,
    val type: HighlightType,
    val related: List<HighlightInfo> = emptyList(),
    val tagProcessorName: String,
)

enum class HighlightType {
    BRACKET,
    TAG,
    TAG_KEY,
    TAG_VALUE,
    COMMENT,
    COMMENT_TAG,
}

fun List<HighlightInfo>.map(mapping: (Int) -> Int): List<HighlightInfo> =
    flatMap {
        it.range.mapToRanges(mapping)
            .map { range ->
                HighlightInfo(
                    range = range,
                    type = it.type,
                    related = it.related.map(mapping),
                    tagProcessorName = it.tagProcessorName,
                )
            }
    }
