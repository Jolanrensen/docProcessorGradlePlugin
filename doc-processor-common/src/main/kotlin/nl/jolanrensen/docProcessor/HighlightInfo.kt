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
