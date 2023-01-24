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
        val queriedPaths = mutableListOf<String>()
        val includePath = tagWithContent.split('\n').first()
            .getTagTarget(tag)

        val subPaths = buildList {
            val current = path.split(".").toMutableList()
            while (current.isNotEmpty()) {
                add(current.joinToString("."))
                current.removeLast()
            }
        }

        // get all possible full @include paths with all possible sub paths
        val includeQueries = subPaths.map {
            includePath.expandPath(currentFullPath = it)
        }

        // query the filtered documentables for the include path
        val queried = includeQueries.firstNotNullOfOrNull { query ->
            queriedPaths.add(query)
            filteredDocumentables[query]?.firstOrNull { it != documentable }
        } ?: run {
            // if the include path is not found, check the imports
            val imports = documentable.getImports()

            imports.firstNotNullOfOrNull {
                val query = if (it.hasStar) {
                    it.pathStr.removeSuffix("*") + includePath
                } else {
                    if (!includePath.startsWith(it.importedName!!.identifier))
                        return@firstNotNullOfOrNull null

                    includePath.replaceFirst(it.importedName!!.identifier, it.pathStr)
                }

                queriedPaths.add(query)
                filteredDocumentables[query]?.firstOrNull { it != documentable }
            }
        }

        // replace the include statement with the kdoc of the queried node (if found) else throw an error
        return queried
            ?.docContent
            ?: error("IncludeDocProcessor ERROR: Include not found: $includePath. Called from $path. Attempted queries: [${queriedPaths.joinToString("\n")}]")
    }
}