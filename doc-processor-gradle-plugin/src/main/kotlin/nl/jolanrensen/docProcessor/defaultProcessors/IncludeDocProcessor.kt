package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocProcessor
import nl.jolanrensen.docProcessor.DocumentableWithSource
import nl.jolanrensen.docProcessor.expandInclude
import nl.jolanrensen.docProcessor.getAtSymbolTargetName
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
class IncludeDocProcessor : DocProcessor {

    val tag = "include"

    private val DocumentableWithSource.hasInclude
        get() = tags.any { it == tag }

    override fun process(
        documentablesByPath: Map<String, List<DocumentableWithSource>>,
    ): Map<String, List<DocumentableWithSource>> {
        // split the documentables into ones that have comments and are linkable and those that can be skipped
        val (include, skipped) = documentablesByPath
            .entries
            .flatMap { (path, docs) -> docs.map { path to it } }
            .partition { (_, it) -> it.documentable.isLinkableElement() && it.docComment != null }

        val mutableSourceDocs = buildMap<String, MutableList<DocumentableWithSource>> {
            for ((path, docs) in include) {
                getOrPut(path) { mutableListOf() }.add(docs)
            }
        }

        var i = 0
        while (mutableSourceDocs.any { it.value.any { it.hasInclude } }) {
            if (i++ > 10_000) {
                val circularRefs = mutableSourceDocs
                    .filter { it.value.any { it.hasInclude } }
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

            mutableSourceDocs
                .filter { it.value.any { it.hasInclude } }
                .forEach { (path, documentables) ->
                    documentables.replaceAll { documentable ->
                        val doc = documentable.docContent
                        val processedDoc = doc
                            .split('\n')
                            .map { line ->
                                if (!line.startsWith("@$tag")) return@map line

                                // get the full include path
                                val includePath = line.getAtSymbolTargetName(tag)
                                val parentPath = path.take(path.lastIndexOf('.').coerceAtLeast(0))
                                val includeQuery = expandInclude(include = includePath, parent = parentPath)

                                // query the tree for the include path
                                val queried = mutableSourceDocs[includeQuery]

                                // replace the include statement with the kdoc of the queried node (if found)
                                queried
                                    ?.firstOrNull { it != documentable }
                                    ?.docContent
                                    ?: line
                            }

                            .joinToString("\n")

                        val wasModified = doc != processedDoc

                        if (wasModified) {
                            val hasInclude = processedDoc
                                .split('\n')
                                .any { it.trim().startsWith("@$tag") }

                            val newTags = documentable.tags.let { tags ->
                                if (hasInclude) {
                                    if (tag !in tags) tags + tag
                                    else tags
                                } else {
                                    tags.filterNot { it == tag }
                                }
                            }

                            documentable.copy(
                                docContent = processedDoc,
                                tags = newTags,
                                isModified = true,
                            )
                        } else {
                            documentable
                        }
                    }
                }
        }

        // add the skipped documentables back to the map
        val finalMap = mutableSourceDocs.toMutableMap()
        for ((path, docs) in skipped) {
            finalMap.getOrPut(path) { mutableListOf() }.add(docs)
        }

        return finalMap
    }
}