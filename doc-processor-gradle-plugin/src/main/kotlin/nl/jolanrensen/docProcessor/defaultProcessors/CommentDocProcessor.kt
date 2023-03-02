package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWrapper
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
 * That said, @comment tags might also be useful to break up doc tag blocks, such as @arg.
 */
class CommentDocProcessor : TagDocProcessor() {

    private val tag = "comment"
    override fun tagIsSupported(tag: String): Boolean =
        tag == this.tag

    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
        filteredDocumentables: Map<String, List<DocumentableWrapper>>,
        allDocumentables: Map<String, List<DocumentableWrapper>>
    ): String = ""

    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
        filteredDocumentables: Map<String, List<DocumentableWrapper>>,
        allDocumentables: Map<String, List<DocumentableWrapper>>
    ): String = ""
}