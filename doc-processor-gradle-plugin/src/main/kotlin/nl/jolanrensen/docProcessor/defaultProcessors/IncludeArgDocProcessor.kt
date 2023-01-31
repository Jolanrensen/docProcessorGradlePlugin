package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWithSource
import nl.jolanrensen.docProcessor.ProcessDocsAction
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.findTagNamesInDocContent

/**
 * @see IncludeArgDocProcessor
 */
const val INCLUDE_ARG_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.IncludeArgDocProcessor"

/**
 * Adds two tags to your arsenal:
 * - `@arg argument content` to declare an argument with content that can be used in the same doc.
 * - `@includeArg argument` to include an argument from the same doc.
 *
 * This can be useful to repeat the same content in multiple places in the same doc, but
 * more importantly, it can be used in conjunction with [IncludeDocProcessor].
 *
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
 * NOTE: If there are multiple `@arg` tags with the same name, the last one processed will be used.
 * The order is: Inline tags: depth-first, left-to-right. Block tags: top-to-bottom.
 */
class IncludeArgDocProcessor : TagDocProcessor() {

    private val useArgumentTag = "includeArg"

    private val declareArgumentTag = "arg"
    override fun tagIsSupported(tag: String): Boolean =
        tag in listOf(useArgumentTag, declareArgumentTag)

    override fun shouldContinue(
        i: Int,
        anyModifications: Boolean,
        parameters: ProcessDocsAction.Parameters,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): Boolean {
        if (i >= parameters.processLimit)
            onProcesError(filteredDocumentables, allDocumentables)

        // We can break out of the recursion if there are no more changes. We don't need to throw an error if an
        // argument is not found, as it might be defined in a different file.
        if (i > 0 && !anyModifications) {
            val argsNotFound = argsNotFound.flatMap { (documentable, args) ->
                args.map { arg -> "`${documentable.path}` -> @$useArgumentTag $arg" }
            }.joinToString("\n")
            println("IncludeArgDocProcessor WARNING: Could not find all arguments:[\n$argsNotFound\n]")
            return false
        }

        return super.shouldContinue(i, anyModifications, parameters, filteredDocumentables, allDocumentables)
    }

    // @arg map for path -> arg name -> value
    private val argMap: MutableMap<DocumentableWithSource, MutableMap<String, String>> = mutableMapOf()

    private val argsNotFound: MutableMap<DocumentableWithSource, MutableSet<String>> = mutableMapOf()
    override fun processTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String {
        // split up the content for @includeArg but not for @arg
        val isIncludeArg = tagWithContent.trimStart().startsWith("@$useArgumentTag")
        return if (isIncludeArg) {
            tagWithContent.split('\n').mapIndexed { i: Int, line: String ->
                // tagWithContent is the content after the @includeArg tag
                // including any new lines below. We will only replace the first line and skip the rest.
                if (i == 0) {
                    processInnerTagWithContent(
                        tagWithContent = line,
                        path = path,
                        documentable = documentable,
                        docContent = docContent,
                        filteredDocumentables = filteredDocumentables,
                        allDocumentables = allDocumentables,
                    )
                } else {
                    line
                }
            }.joinToString("\n")
        } else {
            processInnerTagWithContent(
                tagWithContent = tagWithContent,
                path = path,
                documentable = documentable,
                docContent = docContent,
                filteredDocumentables = filteredDocumentables,
                allDocumentables = allDocumentables,
            )
        }
    }

    override fun processInnerTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String {
        val trimmedTagWithContent = tagWithContent
            .trimStart()
            .removePrefix("{")
            .removeSuffix("}")

        val isArgDeclaration = trimmedTagWithContent.startsWith("@$declareArgumentTag")
        val argTagsStillPresent = docContent.findTagNamesInDocContent().contains(declareArgumentTag)

        return when {
            isArgDeclaration -> {
                val argDeclaration = trimmedTagWithContent
                    .removePrefix("@$declareArgumentTag")
                    .trimStart()
                val name = argDeclaration.takeWhile { !it.isWhitespace() }
                val value = argDeclaration.removePrefix(name).trimStart()

                argMap.getOrPut(documentable) { mutableMapOf() }[name] = value
                argsNotFound.getOrPut(documentable) { mutableSetOf() } -= name
                ""
            }

            argTagsStillPresent -> {
                // skip @includeArg tags if there are still @args present
                tagWithContent
            }

            else -> {
                // skip @includeArg tags if there are still @args present


                val argTarget = trimmedTagWithContent
                    .removePrefix("@$useArgumentTag")
                    .trim()

                val arg = argMap[documentable]?.get(argTarget)
                if (arg == null) {
                    argsNotFound.getOrPut(documentable) { mutableSetOf() } += argTarget
                }

                arg ?: tagWithContent
            }
        }
    }
}