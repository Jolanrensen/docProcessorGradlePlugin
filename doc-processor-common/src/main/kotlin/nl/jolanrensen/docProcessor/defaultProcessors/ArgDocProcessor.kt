package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.*
import nl.jolanrensen.docProcessor.ProgrammingLanguage.JAVA
import java.util.*

/**
 * @see ArgDocProcessor
 */
const val ARG_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.ArgDocProcessor"

/**
 * [Boolean] argument controlling whether to log warnings when an argument is not found.
 * Default is `true`.
 */
const val ARG_DOC_PROCESSOR_LOG_NOT_FOUND = "$ARG_DOC_PROCESSOR.LOG_NOT_FOUND"

@Deprecated("Use 'ARG_DOC_PROCESSOR' instead", ReplaceWith("ARG_DOC_PROCESSOR"))
const val INCLUDE_ARG_DOC_PROCESSOR = ARG_DOC_PROCESSOR

@Deprecated("Use 'ARG_DOC_PROCESSOR_LOG_NOT_FOUND' instead", ReplaceWith("ARG_DOC_PROCESSOR_LOG_NOT_FOUND"))
const val INCLUDE_ARG_DOC_PROCESSOR_LOG_NOT_FOUND = ARG_DOC_PROCESSOR_LOG_NOT_FOUND

/**
 * Adds two tags to your arsenal:
 * - `@setArg argument content` to declare an argument with content that can be used in the same doc.
 * - `@getArg argument` to get an argument from the same doc.
 *
 * This can be useful to repeat the same content in multiple places in the same doc, but
 * more importantly, it can be used in conjunction with [IncludeDocProcessor].
 *
 * All `@setArg` tags are processed before `@getArg` tags.
 *
 * For example:
 *
 * File A:
 * ```kotlin
 * /** NOTE: The {@getArg operation} operation is part of the public API. */
 *  internal interface ApiNote
 * ```
 *
 * File B:
 *```kotlin
 * /**
 *  * Some docs
 *  * @include [ApiNote]
 *  * @setArg operation update
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
 * NOTE: If there are multiple `@setArg` tags with the same name, the last one processed will be used.
 * The order is: Inline tags: depth-first, left-to-right. Block tags: top-to-bottom.
 *
 * NOTE: Use `[References]` as keys if you want extra refactoring-safety.
 * They are queried and saved by their fully qualified name.
 */
class ArgDocProcessor : TagDocProcessor() {

    @Deprecated("")
    private val oldUseArgumentTag = "includeArg"

    @Deprecated("")
    private val oldDeclareArgumentTag = "arg"

    private val useArgumentTag = "getArg"
    private val declareArgumentTag = "setArg"

    override fun tagIsSupported(tag: String): Boolean =
        tag in listOf(
            oldUseArgumentTag,
            oldDeclareArgumentTag,
            useArgumentTag,
            declareArgumentTag,
        )

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
            val log = arguments[ARG_DOC_PROCESSOR_LOG_NOT_FOUND] as? Boolean ?: true
            if (log) {
                val fileTexts = argsNotFound.values
                    .distinctBy { it.doc.file }
                    .associate { it.doc.file to it.doc.file.readText() }

                for ((_, docWithArgs) in argsNotFound) {
                    val (documentable, args) = docWithArgs

                    val (line, char) = fileTexts[documentable.file]!!
                        .getLineAndCharacterOffset(documentable.docFileTextRange.first)

                    if (args.isNotEmpty()) {
                        logger.warn {
                            buildString {
                                val arguments = if (args.size == 1) "argument" else "arguments"
                                appendLine("Could not find @setArg $arguments in doc (${documentable.file.absolutePath}:$line:$char):")
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

    // @setArg map for path -> arg name -> value
    private val argMap: MutableMap<UUID, DocWrapperWithArgMap> = mutableMapOf()

    private val argsNotFound: MutableMap<UUID, DocWrapperWithArgSet> = mutableMapOf()

    // TODO remove if deprecation is gone
    private fun provideNewNameWarning(documentable: DocumentableWrapper) =
        logger.warn {
            buildString {
                val (line, char) = documentable.file
                    .readText()
                    .getLineAndCharacterOffset(documentable.docFileTextRange.first)

                appendLine("Old tag names used in doc: (${documentable.file.absolutePath}:$line:$char)")
                appendLine("The tag name \"$oldUseArgumentTag\" is deprecated. Use \"$useArgumentTag\" instead.")
                appendLine("The tag name \"$oldDeclareArgumentTag\" is deprecated. Use \"$declareArgumentTag\" instead.")
            }
        }

    private fun process(
        tagWithContent: String,
        documentable: DocumentableWrapper,
    ): String {
        val tagName = tagWithContent.getTagNameOrNull()
        val isOldArgDeclaration = tagName == oldDeclareArgumentTag
        val isSetArgDeclaration = tagName == declareArgumentTag

        val tagNames = documentable.docContent.findTagNamesInDocContent()

        val oldDeclareArgTagsStillPresent = oldDeclareArgumentTag in tagNames
        val declareArgTagsStillPresent = declareArgumentTag in tagNames

        val oldUseArgTagsPresent = oldUseArgumentTag in tagNames
        val useArgTagsPresent = useArgumentTag in tagNames

        return when {
            isOldArgDeclaration || isSetArgDeclaration -> { // @setArg
                val argArguments = run {
                    if (isOldArgDeclaration) {
                        provideNewNameWarning(documentable)
                        tagWithContent.getTagArguments(
                            tag = oldDeclareArgumentTag,
                            numberOfArguments = 2,
                        )
                    } else {
                        tagWithContent.getTagArguments(
                            tag = declareArgumentTag,
                            numberOfArguments = 2,
                        )
                    }
                }

                val originalKey = argArguments.first()
                var keys = listOf(originalKey)

                if (originalKey.startsWith('[') && originalKey.contains(']')) {
                    keys = buildReferenceKeys(originalKey, documentable)
                }

                val value = argArguments.getOrElse(1) { "" }
                    .trimStart()
                    .removeSuffix("\n")

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

            declareArgTagsStillPresent || oldDeclareArgTagsStillPresent -> {
                // skip @getArg tags if there are still @setArgs present
                tagWithContent
            }

            else -> { // @getArg
                val includeArgArguments: List<String> = buildList {
                    if (useArgTagsPresent) {
                        this += tagWithContent.getTagArguments(
                            tag = useArgumentTag,
                            numberOfArguments = 2,
                        )
                    }
                    if (oldUseArgTagsPresent) {
                        provideNewNameWarning(documentable)
                        this += tagWithContent.getTagArguments(
                            tag = oldUseArgumentTag,
                            numberOfArguments = 2,
                        )
                    }
                }

                val originalKey = includeArgArguments.first()

                // for stuff written after the @getArg tag, save and include it later
                val extraContent = includeArgArguments.getOrElse(1) { "" }

                var keys = listOf(originalKey)

                // TODO: issue #8: Expanding Java reference links
                if (documentable.programmingLanguage == JAVA && javaLinkRegex in originalKey) {
                    logger.warn {
                        "Java {@link statements} are not replaced by their fully qualified path. " +
                                "Make sure to use fully qualified paths in {@link statements} when " +
                                "using {@link statements} as a key in @setArg."
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