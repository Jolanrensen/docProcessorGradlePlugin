package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.CompletionInfo
import nl.jolanrensen.docProcessor.DocContent
import nl.jolanrensen.docProcessor.DocumentableWrapper
import nl.jolanrensen.docProcessor.HighlightInfo
import nl.jolanrensen.docProcessor.HighlightType
import nl.jolanrensen.docProcessor.TagDocProcessor

/**
 * @see CommentDocProcessor
 */
const val COMMENT_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.CommentDocProcessor"

/**
 * Adds `{@comment tags}` that will be removed from the docs upon processing.
 *
 * For example:
 * ```kotlin
 * /**
 * * {@comment This is a comment}
 * * This is not a comment
 * * @comment This is also a comment
 * * and this too
 * * @otherTag This is not a comment
 * */
 * ```
 * would turn into
 * ```kotlin
 * /**
 * * This is not a comment
 * *
 * * @otherTag This is not a comment
 * */
 * ```
 *
 * NOTE: Careful combining it with [IncludeDocProcessor] as it might remove content in the place
 * where kdoc with a @comment tag is requested to be included. Use inline tags to prevent this.
 * That said, @comment tags might also be useful to break up doc tag blocks, such as @setArg.
 */
class CommentDocProcessor : TagDocProcessor() {

    companion object {
        const val TAG = "comment"
    }

    override val providesTags: Set<String> = setOf(TAG)

    override val completionInfos: List<CompletionInfo>
        get() = listOf(
            CompletionInfo(
                tag = TAG,
                tailText = "Comment something. Will be removed from the docs. Takes any number of arguments.",
            ),
        )

    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = ""

    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = ""

    override fun getHighlightsForInlineTag(
        tagName: String,
        rangeInDocContent: IntRange,
        docContent: DocContent,
    ): List<HighlightInfo> =
        buildList {
            // '{'
            val leftBracket = buildHighlightInfo(
                range = rangeInDocContent.first..rangeInDocContent.first,
                type = HighlightType.COMMENT,
                tag = TAG,
            )

            // '@' and tag name
            this += buildHighlightInfo(
                range = (rangeInDocContent.first + 1)..(rangeInDocContent.first + 1 + tagName.length),
                type = HighlightType.COMMENT_TAG,
                tag = TAG,
            )

            // comment contents
            this += buildHighlightInfo(
                range = (rangeInDocContent.first + 1 + tagName.length + 1)..rangeInDocContent.last - 1,
                type = HighlightType.COMMENT,
                tag = TAG,
            )

            // '}
            val rightBracket = buildHighlightInfo(
                range = rangeInDocContent.last..rangeInDocContent.last,
                type = HighlightType.COMMENT,
                tag = TAG,
            )

            // link left and right brackets
            this += leftBracket.copy(related = listOf(rightBracket))
            this += rightBracket.copy(related = listOf(leftBracket))
        }

    override fun getHighlightsForBlockTag(
        tagName: String,
        rangeInDocContent: IntRange,
        docContent: DocContent,
    ): List<HighlightInfo> =
        buildList {
            // '@' and tag name
            this += buildHighlightInfo(
                range = rangeInDocContent.first..(rangeInDocContent.first + tagName.length),
                type = HighlightType.COMMENT_TAG,
                tag = TAG,
            )

            // comment contents
            this += buildHighlightInfo(
                range = (rangeInDocContent.first + 1 + tagName.length)..rangeInDocContent.last,
                type = HighlightType.COMMENT,
                tag = TAG,
            )
        }
}
