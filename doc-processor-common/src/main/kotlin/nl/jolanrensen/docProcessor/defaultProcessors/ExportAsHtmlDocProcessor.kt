package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.CompletionInfo
import nl.jolanrensen.docProcessor.DocumentableWrapper
import nl.jolanrensen.docProcessor.MutableDocumentableWrapper
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.getTagNameOrNull

/**
 * @see ExportAsHtmlDocProcessor
 */
const val EXPORT_AS_HTML_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.ExportAsHtmlDocProcessor"

/**
 * Adds `@exportAsHtmlStart` and `@exportAsHtmlEnd` tags that cam
 * specify a range of the doc to export to HTML for the [@ExportAsHtml][ExportAsHtml] annotation.
 *
 * - You can use both block- and inline tags.
 * - The range is inclusive, as the tags are stripped from the doc.
 * - The range is specified by the line number of the tag in the doc.
 * - Both tags are optional, if not specified, the start or end of the doc will be used.
 * - Don't use the same tag multiple times; only the first occurrence will be used.
 *
 * @see ExportAsHtml
 */
class ExportAsHtmlDocProcessor : TagDocProcessor() {

    companion object {
        const val EXPORT_AS_HTML_START = "exportAsHtmlStart"
        const val EXPORT_AS_HTML_END = "exportAsHtmlEnd"
    }

    override val providesTags: Set<String> = setOf(EXPORT_AS_HTML_START, EXPORT_AS_HTML_END)

    override val completionInfos: List<CompletionInfo>
        get() = listOf(
            CompletionInfo(
                tag = EXPORT_AS_HTML_START,
                inlineText = "{@$EXPORT_AS_HTML_START}",
                presentableInlineText = "{@$EXPORT_AS_HTML_START}",
                tailText = "Set start of @ExportAsHtml range. Takes no arguments.",
            ),
            CompletionInfo(
                tag = EXPORT_AS_HTML_END,
                inlineText = "{@$EXPORT_AS_HTML_END}",
                presentableInlineText = "{@$EXPORT_AS_HTML_END}",
                tailText = "Set end of @ExportAsHtml range. Takes no arguments.",
            ),
        )

    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String {
        val tag = tagWithContent.getTagNameOrNull() ?: return tagWithContent
        updateHtmlRangeInDoc(tag, documentable)
        val content = tagWithContent.trimStart().removePrefix("@$tag")
        return content
    }

    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String {
        val tag = tagWithContent.getTagNameOrNull() ?: return tagWithContent
        updateHtmlRangeInDoc(tag, documentable)
        return ""
    }

    private fun updateHtmlRangeInDoc(tag: String, documentable: DocumentableWrapper) {
        require(documentable is MutableDocumentableWrapper) {
            "DocumentableWrapper must be MutableDocumentableWrapper to use this processor."
        }
        val lineInDoc = documentable.docContent.value.lines().indexOfFirst {
            it.contains("@$tag")
        }
        when (tag) {
            EXPORT_AS_HTML_START -> documentable.htmlRangeStart = lineInDoc
            EXPORT_AS_HTML_END -> documentable.htmlRangeEnd = lineInDoc
        }
    }
}
