package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocContent
import nl.jolanrensen.docProcessor.DocumentableWithSource
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.decodeCallableTarget
import nl.jolanrensen.docProcessor.getDocContent
import nl.jolanrensen.docProcessor.getTagArguments
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
 *  * Hi!
 *  */
 * ```
 * would turn into
 * ```kotlin
 * /**
 *  * This is the docs of SomeClass
 *  * @see [SomeOtherClass][com.example.somePackage.SomeOtherClass]
 *  * Hi!
 *  */
 * ```
 * [links] that are present in included docs are recognized and replaced by their
 * fully qualified names, so that they still work in the docs.
 *
 * If you need to substitute something in the included docs, you can use [INCLUDE_ARG_DOC_PROCESSOR] in addition to this.
 * For example:
 *
 * File A:
 * ```kotlin
 * /** NOTE: The {@includeArg operation} operation is part of the public API. */
 *  internal interface ApiNote
 * ```
 *
 * File B:
 *```kotlin
 * /**
 *  * Some docs
 *  * @include [ApiNote]
 *  * @arg operation update
 *  */
 * fun update() {}
 * ```
 *
 * File B after processing:
 * ```kotlin
 * /**
 *  * Some docs
 *  * NOTE: The update operation is part of the public API.
 *  */
 * fun update() {}
 * ```
 *
 */
class IncludeDocProcessor : TagDocProcessor() {

    private val tag = "include"

    // Regex to match [Aliased][ReferenceLinks].
    private val aliasedLinkRegex = Regex("""(\[[^\[\]]*]\[)([^\[\]]*)(])""")

    // Regex to match [ReferenceLinks].
    private val singleLinkRegex = Regex("""([^]]\[)([^\[\]]*)(]$|][^\[])""")

    private val javaLinkRegex = Regex("""\{@link.*}""")


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
    override fun onProcesError(
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
        allDocumentables: Map<String, List<DocumentableWithSource>>,
        path: String,
    ): String {
        val includeArguments = line.getTagArguments(tag, 2)
        val includePath = includeArguments.first().decodeCallableTarget()
        // for stuff written after the @include tag, save and include it later
        val extraContent = includeArguments.getOrElse(1) { "" }
            .trimStart()

        // query the filtered documentables for the @include paths
        val queried = documentable.queryDocumentables(
            query = includePath,
            documentables = filteredDocumentables,
        ) { it != documentable }

        if (queried == null) {
            val queriedNoFilter = documentable.queryDocumentables(
                query = includePath,
                documentables = filteredDocumentables,
            )
            val attemptedQueries = documentable.getAllFullPathsFromHereForTargetPath(includePath)
                .joinToString("\n")

            error(
                if (queriedNoFilter == documentable) {
                    "IncludeDocProcessor ERROR: Self-reference detected. Called from \"$path\"."
                } else {
                    "IncludeDocProcessor ERROR: Include not found: \"$includePath\". Called from \"$path\". Attempted queries: [\n$attemptedQueries]"
                }
            )
        }

        var content: DocContent = queried.docContent
            .removeSurrounding("\n")

        if (extraContent.isNotEmpty()) {
            content = "$content $extraContent"
        }

        // if the content contains links to other elements, we need to expand the path
        // providing the original name or alias as new alias.
        content = if (documentable.isKotlin) {
            content.replace(aliasedLinkRegex) { // replace all [Aliased][ReferenceLinks] with [Aliased][ExpandedPath]
                it.groupValues.let {
                    buildString {
                        append(it[1])
                        append(
                            queried.queryDocumentables(
                                query = it[2],
                                documentables = allDocumentables,
                            )?.path ?: it[2]
                        )
                        append(it[3])
                    }
                }
            }.replace(singleLinkRegex) { // replace all [ReferenceLinks] with [ReferenceLinks][ExpandedPath]
                it.groupValues.let {
                    buildString {
                        append(it[0].dropLastWhile { it != ']' })
                        append(it[1].drop(1))
                        append(
                            queried.queryDocumentables(
                                query = it[2],
                                documentables = allDocumentables,
                            )?.path ?: it[2]
                        )
                        append(it[3])
                    }
                }
            }

        } else {
            // TODO?: Java {@link ReferenceLinks}
            if (javaLinkRegex in content) {
                println(
                    "Java {@link statements} are not replaced by their fully qualified path. " +
                            "Make sure to use fully qualified paths in {@link statements} when " +
                            "@including docs with {@link statements}."
                )
            }
            content
        }

        // replace the include statement with the kdoc of the queried node (if found)
        return content
    }

    override fun processInnerTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>,
    ): String = processContent(
        // processContent can handle inner tags perfectly fine
        line = tagWithContent,
        documentable = documentable,
        filteredDocumentables = filteredDocumentables,
        allDocumentables = allDocumentables,
        path = path,
    )

    override fun processTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>,
    ): String = tagWithContent.split('\n').mapIndexed { i: Int, line: String ->
        // tagWithContent is the content after the @include tag, e.g. "[SomeClass]"
        // including any new lines below. We will only replace the first line and skip the rest.
        if (i == 0) {
            processContent(
                line = line,
                documentable = documentable,
                filteredDocumentables = filteredDocumentables,
                allDocumentables = allDocumentables,
                path = path,
            )
        } else {
            line
        }
    }.joinToString("\n")

}
