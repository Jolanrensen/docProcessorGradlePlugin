package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.*
import nl.jolanrensen.docProcessor.ProgrammingLanguage.JAVA
import java.util.*

/**
 * @see IncludeArgDocProcessor
 */
const val INCLUDE_ARG_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.IncludeArgDocProcessor"

/**
 * [Boolean] argument controlling whether to log warnings when an argument is not found.
 * Default is `true`.
 */
const val INCLUDE_ARG_DOC_PROCESSOR_LOG_NOT_FOUND = "$INCLUDE_ARG_DOC_PROCESSOR.LOG_NOT_FOUND"

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
 * NOTE: Use `[References]` as keys if you want extra refactoring-safety.
 * They are queried and saved by their fully qualified name.
 */
class IncludeArgDocProcessor : TagDocProcessor() {

    private val useArgumentTag = "includeArg"

    private val declareArgumentTag = "arg"
    override fun tagIsSupported(tag: String): Boolean =
        tag in listOf(useArgumentTag, declareArgumentTag)

    data class DocWrapperWithArgMap(
        val doc: DocumentableWrapper,
        val args: MutableMap<String, String> = mutableMapOf(),
    )

    data class DocWrapperWithArgSet(
        val doc: DocumentableWrapper,
        val args: MutableSet<String> = mutableSetOf(),
    )

    override fun shouldContinue(
        i: Int,
        anyModifications: Boolean,
        processLimit: Int,
    ): Boolean {
        val processLimitReached = i >= processLimit
        if (processLimitReached)
            onProcessError()

        val atLeastOneRun = i > 0

        // We can break out of the recursion if there are no more changes. We don't need to throw an error if an
        // argument is not found, as it might be defined in a different file.
        if (atLeastOneRun && !anyModifications) {
            val log = arguments[INCLUDE_ARG_DOC_PROCESSOR_LOG_NOT_FOUND] as? Boolean ?: true
            if (log) {
                val fileTexts = argsNotFound.map { (uuid, it) ->
                    uuid to it.doc.file.readText()
                }.toMap()

                for ((identifier, doc) in argsNotFound) {
                    val (documentable, args) = doc

                    val (line, char) = fileTexts[identifier]!!
                        .getLineAndCharacterOffset(documentable.docFileTextRange.first)

                    if (args.isNotEmpty()) {
                        logger.warn {
                            buildString {
                                val arguments = if (args.size == 1) "argument" else "arguments"
                                appendLine("Could not find @arg $arguments in doc (${documentable.file.absolutePath}:$line:$char):")
                                appendLine(args.joinToString(",\n") { "  \"@$useArgumentTag $it\"" })
                            }
                        }
                    }
                }
            }

            return false
        }

        return super.shouldContinue(i, anyModifications, processLimit)
    }

    // @arg map for path -> arg name -> value
    private val argMap: MutableMap<UUID, DocWrapperWithArgMap> = mutableMapOf()

    private val argsNotFound: MutableMap<UUID, DocWrapperWithArgSet> = mutableMapOf()

    private fun process(
        tagWithContent: String,
        documentable: DocumentableWrapper,
    ): String {
        val tagName = tagWithContent.getTagNameOrNull()
        val isArgDeclaration = tagName == declareArgumentTag

        val argTagsStillPresent = declareArgumentTag in documentable.docContent.findTagNamesInDocContent()

        return when {
            isArgDeclaration -> { // @arg
                val argArguments = tagWithContent.getTagArguments(
                    tag = declareArgumentTag,
                    numberOfArguments = 2,
                )
                val originalKey = argArguments.first()
                var keys = listOf(originalKey)

                if (originalKey.startsWith('[') && originalKey.contains(']')) {
                    keys = buildReferenceKeys(originalKey, documentable)
                }

                val value = argArguments.getOrElse(1) { "" }
                    .trimStart()
                    .removeSuffix("\n")
                    .removeEscapeCharacters()

                argMap.getOrPut(documentable.identifier) {
                    DocWrapperWithArgMap(documentable)
                }.also { (_, args) ->
                    for (it in keys) args[it] = value
                }

                argsNotFound.getOrPut(documentable.identifier) {
                    DocWrapperWithArgSet(documentable)
                }.args -= keys.toSet()
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

                val originalKey = includeArgArguments.first()

                // for stuff written after the @includeArg tag, save and include it later
                val extraContent = includeArgArguments.getOrElse(1) { "" }

                var keys = listOf(originalKey)

                // TODO: issue #8: Expanding Java reference links
                if (documentable.programmingLanguage == JAVA && javaLinkRegex in originalKey) {
                    logger.warn {
                        "Java {@link statements} are not replaced by their fully qualified path. " +
                                "Make sure to use fully qualified paths in {@link statements} when " +
                                "using {@link statements} as a key in @arg."
                    }
                }

                if (originalKey.startsWith('[') && originalKey.contains(']')) {
                    keys = buildReferenceKeys(originalKey, documentable)
                }

                val content = keys.firstNotNullOfOrNull { key ->
                    argMap[documentable.identifier]?.args?.get(key)
                }
                when {
                    content == null -> {
                        argsNotFound.getOrPut(documentable.identifier) {
                            DocWrapperWithArgSet(documentable)
                        }.args += keys
                        tagWithContent
                    }

                    extraContent.isNotEmpty() -> buildString {
                        append(content)
                        if (!extraContent.first().isWhitespace())
                            append(" ")
                        append(extraContent)
                    }

                    else -> content
                }
            }
        }
    }

    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = process(
        tagWithContent = tagWithContent,
        documentable = documentable,
    )

    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = process(
        tagWithContent = tagWithContent,
        documentable = documentable,
    )

    private fun buildReferenceKeys(
        originalKey: String,
        documentable: DocumentableWrapper,
    ): List<String> {
        var keys = listOf(originalKey)
        val reference = originalKey.decodeCallableTarget()
        val referencedDocumentable = documentable.queryDocumentables(
            query = reference,
            documentables = documentablesByPath,
        )

        if (referencedDocumentable != null)
            keys = listOfNotNull(
                referencedDocumentable.fullyQualifiedPath,
                referencedDocumentable.fullyQualifiedExtensionPath,
            ).map { "[$it]" }

        return keys
    }
}