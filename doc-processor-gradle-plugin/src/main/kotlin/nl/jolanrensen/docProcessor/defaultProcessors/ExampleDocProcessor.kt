package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWithSource
import nl.jolanrensen.docProcessor.TagDocProcessor

class ExampleDocProcessor : TagDocProcessor() {

    /** We'll intercept @example tags. */
    override fun tagIsSupported(tag: String): Boolean =
        tag == "example"

    /** How `{@inner tags}` are processed. */
    override fun processInnerTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>,
    ): String = processContent(
        tagWithContent
            .removePrefix("{")
            .removeSuffix("}")
    )

    /** How `  @normal tags` are processed. */
    override fun processTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>,
    ): String = processContent(
        tagWithContent.trimStart()
    )

    // We can use the same function for both processInnerTagWithContent and processTagWithContent
    private fun processContent(tagWithContent: String): String {
        // We can get the content after the @example tag.
        val contentWithoutTag = tagWithContent
            .removePrefix("@example")
            .removeSurrounding("\n")
            .trim()

        // While we can play with the other arguments, let's just return some simple modified content

        return "Hi from the example doc processor! Here's the content after the @example tag: \"$contentWithoutTag\""
    }
}