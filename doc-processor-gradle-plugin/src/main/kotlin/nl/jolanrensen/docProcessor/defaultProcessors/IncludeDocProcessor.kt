package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWithSource
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.expandPath
import nl.jolanrensen.docProcessor.getTagTarget
import nl.jolanrensen.docProcessor.getDocContent
import nl.jolanrensen.docProcessor.isLinkableElement
import nl.jolanrensen.docProcessor.toDoc

/**
 * @see IncludeDocProcessor
 */
const val INCLUDE_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.IncludeDocProcessor"

/**
 * Allows you to @include docs from other linkable elements.
 *
 * For example:
 * ```kotlin
 * /**
 *  * @include [SomeClass]
 *  */
 * ```
 * would turn into
 * ```kotlin
 * /**
 *  * This is the docs of SomeClass
 *  */
 */
class IncludeDocProcessor : TagDocProcessor() {

    private val tag = "include"

    override fun tagIsSupported(tag: String): Boolean = tag == this.tag

    /**
     * Filter documentables to only include linkable elements (classes, functions, properties, etc) and
     * have any documentation. This will save performance when looking up the target of the @include tag.
     */
    override fun <T : DocumentableWithSource> filterDocumentables(documentable: T): Boolean =
        documentable.documentable.isLinkableElement() &&
                documentable.docComment != null

    /**
     * Provides a helpful message when a circular reference is detected.
     */
    override fun onProcesLimitReached(
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): Nothing {
        val circularRefs = filteredDocumentables
            .filter { it.value.any { it.hasASupportedTag } }
            .entries
            .joinToString("\n\n") { (path, documentables) ->
                buildString {
                    appendLine("$path:")
                    appendLine(documentables.joinToString("\n\n") {
                        it.queryFile()?.getDocContent()?.toDoc(4) ?: ""
                    })
                }
            }
        error("Circular references detected in @include statements:\n$circularRefs")
    }

    /**
     * Queries the path targeted by the @include tag and returns the docs of that element to
     * overwrite the @include tag.
     */
    override fun processTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String {
        // get the full @include path
        val includePath = tagWithContent.getTagTarget(tag)
        val includeQuery = includePath.expandPath(currentFullPath = path)

        // query the filtered documentables for the include path
        val queried = filteredDocumentables[includeQuery]

        // replace the include statement with the kdoc of the queried node (if found) else return the original
        return queried
            ?.firstOrNull { it != documentable }
            ?.docContent
            ?: error("IncludeDocProcessor ERROR: Include not found: $includeQuery. Called from $path")
    }
}