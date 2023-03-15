package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocContent
import nl.jolanrensen.docProcessor.DocumentableWrapper
import nl.jolanrensen.docProcessor.ProgrammingLanguage.JAVA
import nl.jolanrensen.docProcessor.ProgrammingLanguage.KOTLIN
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.decodeCallableTarget
import nl.jolanrensen.docProcessor.getDocContentOrNull
import nl.jolanrensen.docProcessor.getTagArguments
import nl.jolanrensen.docProcessor.isLinkableElement
import nl.jolanrensen.docProcessor.javaLinkRegex
import nl.jolanrensen.docProcessor.removeEscapeCharacters
import nl.jolanrensen.docProcessor.replaceKdocLinks
import nl.jolanrensen.docProcessor.toDoc
import org.apache.commons.lang.StringEscapeUtils

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
 * NOTE: `[links]` that are present in included docs are recognized and replaced by their
 * fully qualified names, so that they still work in the docs. If you don't want this to happen,
 * simply break the link like [this\]. The escape character will be removed upon @include processing and the link will
 * not be replaced.
 *
 * NOTE: Newlines at the beginning and end of included doc are removed, making including:
 * ```kotlin
 * /** Hello */
 * ```
 * equivalent to including:
 * ```kotlin
 * /**
 *  * Hello
 *  */
 * ```
 *
 * NOTE: If you need to substitute something in the included docs, you can use [INCLUDE_ARG_DOC_PROCESSOR] in addition to this.
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

    override fun tagIsSupported(tag: String): Boolean = tag == this.tag

    /**
     * Filter documentables to only include linkable elements (classes, functions, properties, etc) and
     * have any documentation. This will save performance when looking up the target of the @include tag.
     */
    override fun <T : DocumentableWrapper> filterDocumentables(documentable: T): Boolean =
        documentable.documentable.isLinkableElement() &&
                documentable.sourceHasDocumentation

    /**
     * Provides a helpful message when a circular reference is detected.
     */
    override fun onProcessError(): Nothing {
        val circularRefs = filteredDocumentables
            .filter { it.value.any { it.hasSupportedTag } }
            .entries
            .joinToString("\n\n") { (path, documentables) ->
                buildString {
                    appendLine("$path:")
                    appendLine(documentables.joinToString("\n\n") {
                        it.queryFileForDocTextRange().getDocContentOrNull()?.toDoc(4) ?: ""
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
        documentable: DocumentableWrapper,
        path: String,
    ): String {
        val includeArguments = line.getTagArguments(tag = tag, numberOfArguments = 2)
        val includePath = includeArguments.first().decodeCallableTarget()
        // for stuff written after the @include tag, save and include it later
        val extraContent = includeArguments.getOrElse(1) { "" }

        // query the filtered documentables for the @include paths
        val targetDocumentable = documentable.queryDocumentables(
            query = includePath,
            documentables = filteredDocumentables,
        ) { it != documentable }

        if (targetDocumentable == null) {
            val targetDocumentableNoFilter = documentable.queryDocumentables(
                query = includePath,
                documentables = filteredDocumentables,
            )
            val attemptedQueries = documentable.getAllFullPathsFromHereForTargetPath(includePath)
                .joinToString("\n") { "|  $it" }

            val targetPath = documentable.queryDocumentablesForPath(
                query = includePath,
                documentables = allDocumentables,
            )

            error(
                when {
                    targetDocumentableNoFilter == documentable ->
                        "Self-reference detected."

                    targetPath != null ->
                        """
                        |Reference found, but no documentation found for: "$includePath".
                        |Including documentation from outside the library or from type-aliases is currently not supported.
                        |Attempted queries: [
                        $attemptedQueries
                        |]
                        """.trimMargin()

                    else ->
                        """
                        |Reference not found: "$includePath".
                        |Attempted queries: [
                        $attemptedQueries
                        |]
                        """.trimMargin()
                }
            )
        }

        var targetContent: DocContent = targetDocumentable.docContent
            .removePrefix("\n")
            .removeSuffix("\n")

        if (extraContent.isNotEmpty()) {
            targetContent = buildString {
                append(targetContent)
                if (!extraContent.first().isWhitespace())
                    append(" ")
                append(extraContent)
            }
        }

        targetContent = when (documentable.programmingLanguage) {
            // if the content contains links to other elements, we need to expand the path
            // providing the original name or alias as new alias.
            KOTLIN -> targetContent.replaceKdocLinks { query ->
                targetDocumentable.queryDocumentablesForPath(
                    query = query,
                    documentables = allDocumentables,
                    pathIsValid = { path, it ->
                        // ensure that the given path points to the same element in the destination place and
                        // that it's queryable
                        documentable.queryDocumentables(
                            query = path,
                            documentables = allDocumentables,
                        ) == it
                    },
                ) ?: query
            }

            JAVA -> {
                // TODO: issue #8: Expanding Java reference links
                if (javaLinkRegex in targetContent) {
                    logger.warn {
                        "Java {@link statements} are not replaced by their fully qualified path. " +
                                "Make sure to use fully qualified paths in {@link statements} when " +
                                "@including docs with {@link statements}."
                    }
                }

                // Escape HTML characters in Java docs
                StringEscapeUtils.escapeHtml(targetContent)
                    .replace("@", "&#64;")
                    .replace("*/", "&#42;&#47;")
            }
        }

        // replace the include statement with the kdoc of the queried node (if found)
        return targetContent.removeEscapeCharacters()
    }

    /**
     * How to process the `{@include tag}` when it's inline.
     *
     * [processContent] can handle inner tags perfectly fine.
     */
    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = processContent(
        line = tagWithContent,
        documentable = documentable,
        path = path,
    )

    /**
     * How to process the `@include tag` when it's a block tag.
     *
     * [tagWithContent] is the content after the `@include tag`, e.g. `"[SomeClass]"`
     * including any new lines below.
     * We will only replace the first line and skip the rest.
     */
    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = processContent(
        line = tagWithContent,
        documentable = documentable,
        path = path,
    )
}
