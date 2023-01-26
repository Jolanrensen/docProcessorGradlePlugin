package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWithSource
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.expandPath
import nl.jolanrensen.docProcessor.getDocContent
import nl.jolanrensen.docProcessor.getTagTarget
import nl.jolanrensen.docProcessor.hasStar
import nl.jolanrensen.docProcessor.isLinkableElement
import nl.jolanrensen.docProcessor.toDoc

/**
 * @see IncludeDocProcessor
 */
const val INCLUDE_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.IncludeDocProcessor"

/**
 * Allows you to @include docs from other linkable elements.
 * `@include` keeps the content of the block below the include statement intact.
 *
 * For example:
 * ```kotlin
 * /**
 *  * @include [SomeClass]
 *  * Hi
 *  */
 * ```
 * would turn into
 * ```kotlin
 * /**
 *  * This is the docs of SomeClass
 *  * Hi
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
    private fun processContent(
        line: String,
        documentable: DocumentableWithSource,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        path: String
    ): String {
        val includePath = line.getTagTarget(tag)

        val queries = documentable.getAllFullPathsFromHereForTargetPath(includePath)

        // query the filtered documentables for the @include paths
        val queried = queries.firstNotNullOfOrNull { query ->
            filteredDocumentables[query]?.firstOrNull { it != documentable }
        }

        queried ?: error(
            "IncludeDocProcessor ERROR: Include not found: \"$includePath\". Called from \"$path\". Attempted queries: [\n${
                queries.joinToString("\n")
            }]"
        )

        // replace the include statement with the kdoc of the queried node (if found)
        return queried.docContent
    }

    override fun processInnerTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String = processContent( // processContent can handle inner tags perfectly fine
        line = tagWithContent,
        documentable = documentable,
        filteredDocumentables = filteredDocumentables,
        path = path,
    )

    override fun processTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String = tagWithContent.split('\n').mapIndexed { i: Int, line: String ->
        // tagWithContent is the content after the @include tag, e.g. "[SomeClass]"
        // including any new lines below. We will only replace the first line and skip the rest.
        if (i == 0) {
            processContent(
                line = line,
                documentable = documentable,
                filteredDocumentables = filteredDocumentables,
                path = path,
            )
        } else {
            line
        }
    }.joinToString("\n")

}