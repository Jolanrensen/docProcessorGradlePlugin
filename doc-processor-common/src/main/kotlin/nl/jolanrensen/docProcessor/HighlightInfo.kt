package nl.jolanrensen.docProcessor

data class HighlightInfo(val range: IntRange, val type: HighlightType)

enum class HighlightType {
    BRACKET,
    TAG,
    TAG_KEY,
    TAG_VALUE,
    COMMENT,
    COMMENT_TAG,
}
