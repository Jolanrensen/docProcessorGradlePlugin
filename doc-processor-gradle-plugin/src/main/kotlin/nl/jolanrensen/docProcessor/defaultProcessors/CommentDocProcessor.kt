package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWithSource
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
 */
class CommentDocProcessor : TagDocProcessor() {

    private val tag = "comment"
    override fun tagIsSupported(tag: String): Boolean =
        tag == this.tag

    override fun processTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String = ""

    override fun processInnerTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String = ""
}