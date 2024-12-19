package nl.jolanrensen.docProcessor

/**
 * Represents a single highlight in a KDoc.
 *
 * @param range The range of the highlight in the [DocContent] (or [DocText] if desired).
 * @param type The type of the highlight, [HighlightType].
 * @param related Other highlights that are related to this one, like matching brackets.
 *   When this [range] is touched, it and the [related] ranges will pop visually.
 * @param tagProcessorName The name of the tag processor that created this highlight.
 * @param description An optional description of the tag processor that created this highlight.
 * @see [TagDocProcessor.buildHighlightInfo] for creating these from inside a [TagDocProcessor].
 */
data class HighlightInfo(
    val range: IntRange,
    val type: HighlightType,
    val related: List<HighlightInfo> = emptyList(),
    val tagProcessorName: String,
    val description: String,
)

enum class HighlightType {

    /** like `{}`; will render like @Annotation + bold, italic */
    BRACKET,

    /** like `@tag`; will render like @Annotation + bold, italic, underscore */
    TAG,

    /** like `@tag KEY`; will render like []-links in KDocs */
    TAG_KEY,

    /**
     * like `@tag KEY VALUE`, mostly used for "default" values;
     * will render like declarations, such as classNames outside KDocs
     */
    TAG_VALUE,

    /** for comment contents and brackets, will render like block-comments + bold, italic */
    COMMENT,

    /** for `@comment` in comments, will render like block-comments + bold, italic, underscore */
    COMMENT_TAG,
}

/**
 * Applies a mapping to the ranges of the highlights.
 *
 * Can split 1 [HighlightInfo] into multiple [HighlightInfo]s if the range is split.
 * Does not join back.
 */
fun List<HighlightInfo>.applyMapping(mapping: (Int) -> Int): List<HighlightInfo> =
    flatMap {
        it.range.mapToRanges(mapping)
            .map { range ->
                HighlightInfo(
                    range = range,
                    type = it.type,
                    related = it.related.applyMapping(mapping),
                    tagProcessorName = it.tagProcessorName,
                    description = it.description,
                )
            }
    }
