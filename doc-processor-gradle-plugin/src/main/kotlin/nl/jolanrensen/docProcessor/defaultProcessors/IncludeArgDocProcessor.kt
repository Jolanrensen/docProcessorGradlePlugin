package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWithSource
import nl.jolanrensen.docProcessor.ProcessDocsAction
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.decodeCallableTarget
import nl.jolanrensen.docProcessor.findTagNamesInDocContent
import nl.jolanrensen.docProcessor.getTagArguments
import nl.jolanrensen.docProcessor.getTagNameOrNull
import nl.jolanrensen.docProcessor.removeEscapeCharacters

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
 * NOTE: Use `[References]` as keys is you want extra refactoring-safety.
 * They are queried and saved by their fully qualified name.
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

    private fun process(
        tagWithContent: String,
        documentable: DocumentableWithSource,
        docContent: String,
        allDocumentables: Map<String, List<DocumentableWithSource>>,
    ): String {
        val tagName = tagWithContent.getTagNameOrNull()
        val isArgDeclaration = tagName == declareArgumentTag

        val argTagsStillPresent = declareArgumentTag in docContent.findTagNamesInDocContent()

        return when {
            isArgDeclaration -> { // @arg
                val argArguments = tagWithContent.getTagArguments(
                    tag = declareArgumentTag,
                    numberOfArguments = 2,
                )
                var key = argArguments.first()

                if (key.startsWith('[') && key.contains(']')) {
                    key = buildReferenceKey(key, documentable, allDocumentables)
                }

                val value = argArguments.getOrElse(1) { "" }
                    .trimStart()
                    .removeSuffix("\n")
                    .removeEscapeCharacters()

                argMap.getOrPut(documentable) { mutableMapOf() }[key] = value
                argsNotFound.getOrPut(documentable) { mutableSetOf() } -= key
                ""
            }

            argTagsStillPresent -> {
                // skip @includeArg tags if there are still @args present
                tagWithContent
            }

            else -> { // @includeArg
                val includeArgArguments = tagWithContent.getTagArguments(
                    tag = useArgumentTag,
                    numberOfArguments = 2,
                )

                val argIncludeDeclaration = includeArgArguments.first()

                // for stuff written after the @includeArg tag, save and include it later
                val extraContent = includeArgArguments.getOrElse(1) { "" }
                    .trimStart()

                var key = argIncludeDeclaration
                if (key.startsWith('[') && key.contains(']')) {
                    key = buildReferenceKey(key, documentable, allDocumentables)
                }

                val content = argMap[documentable]?.get(key)
                if (content == null) {
                    argsNotFound.getOrPut(documentable) { mutableSetOf() } += key

                    tagWithContent
                } else {
                    if (extraContent.isEmpty()) content
                    else "$content $extraContent"
                }
            }
        }
    }

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

        return if (isIncludeArg) { // @includeArg
            tagWithContent.split('\n').mapIndexed { i: Int, line: String ->
                // tagWithContent is the content after the @includeArg tag
                // including any new lines below. We will only replace the first line and skip the rest.
                if (i == 0) {
                    process(
                        tagWithContent = line,
                        documentable = documentable,
                        docContent = docContent,
                        allDocumentables = allDocumentables,
                    )
                } else {
                    line
                }
            }.joinToString("\n")
        } else { // @arg
            process(
                tagWithContent = tagWithContent,
                documentable = documentable,
                docContent = docContent,
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
        allDocumentables: Map<String, List<DocumentableWithSource>>,
    ): String = process(
        tagWithContent = tagWithContent,
        documentable = documentable,
        docContent = docContent,
        allDocumentables = allDocumentables,
    )

    private fun buildReferenceKey(
        originalKey: String,
        documentable: DocumentableWithSource,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String {
        var key = originalKey
        val reference = key.decodeCallableTarget()
        val referencedDocumentable = documentable.queryDocumentables(
            query = reference,
            documentables = allDocumentables,
        )

        if (referencedDocumentable != null)
            key = "[${referencedDocumentable.path}]"
        return key
    }
}