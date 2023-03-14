package nl.jolanrensen.docProcessor

import org.jetbrains.kotlin.idea.editor.fixers.start

/**
 * Specific [DocProcessor] that processes just tags.
 *
 * Extending classes need to define which tags they can handle in [tagIsSupported],
 * how they handle block tags and inline tags in [processBlockTagWithContent] and [processInlineTagWithContent],
 * respectively.
 *
 * [filterDocumentables] can be overridden to change the value of `filteredDocumentables` in
 * [processBlockTagWithContent], and [processInlineTagWithContent] and which documentables are processed at all.
 *
 * [onProcessError] can be overridden to change the error message when the process limit is reached
 * (defined in [ProcessDocsAction.Parameters.processLimit] and enforced in [shouldContinue]).
 *
 * [shouldContinue] can also be overridden to change the conditions under which the process's while-loops should
 * continue or stop and when an error should be thrown.
 * By default, it will throw an error if the process limit (defined in [ProcessDocsAction.Parameters.processLimit])
 * is reached and if supported tags are present but no modifications are made (indicating an infinite loop).
 */
abstract class TagDocProcessor : DocProcessor() {

    /** The tags to be replaced, like "sample" */
    abstract fun tagIsSupported(tag: String): Boolean

    /** Returns true if [tagIsSupported] is in this [DocumentableWrapper]. */
    val DocumentableWrapper.hasSupportedTag
        get() = tags.any(::tagIsSupported)

    /** All documentables in the source set. */
    val allDocumentables: Map<String, List<DocumentableWrapper>>
        get() = allMutableDocumentables

    /**
     * Filtered documentables by [filterDocumentables].
     * The [DocumentableWrapper]s in this map are the same objects as in [allDocumentables], just a subset.
     */
    val filteredDocumentables: Map<String, List<DocumentableWrapper>>
        get() = filteredMutableDocumentable

    /** All (mutable) documentables in the source set. */
    private lateinit var allMutableDocumentables: Map<String, List<MutableDocumentableWrapper>>

    /**
     * Filtered (mutable) documentables by [filterDocumentables].
     * The [DocumentableWrapper]s in this map are the same objects as in [allDocumentables], just a subset.
     */
    private val filteredMutableDocumentable: Map<String, List<MutableDocumentableWrapper>> by lazy {
        allMutableDocumentables
            .mapValues { (_, documentables) ->
                documentables.filter(::filterDocumentables)
            }.filterValues { it.isNotEmpty() }
    }

    /**
     * Converts a [Map]<[String], [List]<[DocumentableWrapper]>> to
     * [Map]<[String], [List]<[MutableDocumentableWrapper]>>.
     *
     * The [MutableDocumentableWrapper] is a copy of the original [DocumentableWrapper].
     */
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, List<DocumentableWrapper>>.toMutable(): Map<String, List<MutableDocumentableWrapper>> =
        mapValues { (_, documentables) ->
            if (documentables.all { it is MutableDocumentableWrapper }) {
                documentables as List<MutableDocumentableWrapper>
            } else {
                documentables.map { it.asMutable() }
            }
        }

    /**
     * Optionally, you can filter the [DocumentableWrapper]s that will appear in
     * [processBlockTagWithContent] and [processInlineTagWithContent] as `filteredDocumentables`.
     * This can be useful as an optimization.
     *
     * This filter will also be applied to all the docs that are processed. Normally, all docs with
     * a supported tag will be processed. If you want to filter them more strictly, override this method too.
     */
    open fun <T : DocumentableWrapper> filterDocumentables(documentable: T): Boolean = true

    /**
     * Provide a meaningful error message when the given process limit is reached.
     *
     * @return [Nothing] must throw an error
     * @throws [IllegalStateException] On proces limit reached
     */
    @Throws(IllegalStateException::class)
    open fun onProcessError(): Nothing = error("Process limit reached")

    /**
     * Check the recursion limit to break the while loop.
     * By default, it will call [onProcessError] if the process limit (defined in [ProcessDocsAction.Parameters.processLimit])
     * is reached and if supported tags are present but no modifications are made (indicating an infinite loop).
     *
     * @param [i] The current iteration.
     * @param [anyModifications] `true` if any modifications were made, `false` otherwise.
     * @param [parameters] The [ProcessDocsAction.Parameters] used to process the docs.
     * @return `true` if recursion limit is reached, `false` otherwise.
     */
    @Throws(IllegalStateException::class)
    open fun shouldContinue(
        i: Int,
        anyModifications: Boolean,
        parameters: ProcessDocsAction.Parameters,
    ): Boolean {
        val processLimitReached = i >= parameters.processLimit

        val hasSupportedTags = filteredDocumentables.any { it.value.any { it.hasSupportedTag } }
        val atLeastOneRun = i > 0
        val tagsArePresentButNoModifications = hasSupportedTags && !anyModifications && atLeastOneRun

        // Throw error if process limit is reached or if supported tags keep being present but no modifications are made
        if (processLimitReached || tagsArePresentButNoModifications)
            onProcessError()

        return hasSupportedTags
    }

    /**
     * Process a block tag with content ([tagWithContent]) into whatever you like.
     *
     * @param [tagWithContent] Tag with content block to process.
     *  For example: `@tag Some content`
     *  Can contain newlines, and does include tag.
     *  The block continues until the next tag block starts.
     * @param [path] The path of the doc where the tag is found.
     * @param [documentable] The Documentable beloning to the current tag.
     *
     * @return A [String] that will replace the tag with content entirely.
     */
    abstract fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String

    /**
     * Process an inline tag with content ([tagWithContent]) into whatever you like.
     *
     * @param [tagWithContent] Tag with content to process.
     *  For example: `{@tag Some content}`
     *  Contains {} and tag.
     * @param [path] The path of the doc where the tag is found.
     * @param [documentable] The Documentable beloning to the current tag.
     *
     * @return A [String] that will replace the tag with content entirely.
     */
    abstract fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String

    /**
     * This is the main function that will be called by the [DocProcessor].
     * It will process all documentables found in the source set and replace all
     * supported tags ([tagIsSupported]) and their content with the result of [processBlockTagWithContent]
     * and [processInlineTagWithContent].
     * The processing will continue until there are no more supported tags found in all
     * filtered documentables. This means that recursion (a.k.a tags that create other supported tags)
     * is possible. However, there is a limit to prevent infinite recursion ([ProcessDocsAction.Parameters.processLimit]).
     */
    override fun process(
        parameters: ProcessDocsAction.Parameters,
        documentablesByPath: Map<String, List<DocumentableWrapper>>,
    ): Map<String, List<DocumentableWrapper>> {
        // Convert to mutable
        allMutableDocumentables = documentablesByPath.toMutable()

        // Main recursion loop that will continue until all supported tags are replaced
        // or the process limit is reached.
        var i = 0
        while (true) {
            val filteredDocumentablesWithTag = filteredMutableDocumentable
                .filter { it.value.any { it.hasSupportedTag } }

            var anyModifications = false
            for ((path, documentables) in filteredDocumentablesWithTag) {
                for (documentable in documentables) {
                    if (!documentable.hasSupportedTag) continue

                    val docContent = documentable.docContent
                    val processedDoc = processTagsInContent(
                        docContent = docContent,
                        path = path,
                        documentable = documentable,
                        parameters = parameters,
                    )

                    val wasModified = docContent != processedDoc
                    if (wasModified) {
                        anyModifications = true
                        val tags = processedDoc.findTagNamesInDocContent().toSet()

                        documentable.apply {
                            this.docContent = processedDoc
                            this.tags = tags
                            this.isModified = true
                        }
                    }
                }
            }

            val shouldContinue = shouldContinue(
                i = i++,
                anyModifications = anyModifications,
                parameters = parameters,
            )
            if (!shouldContinue) break
        }

        return allDocumentables
    }

    private fun processTagsInContent(
        docContent: DocContent,
        path: String,
        documentable: MutableDocumentableWrapper,
        parameters: ProcessDocsAction.Parameters,
    ): DocContent {
        // Process the inline tags first
        val processedInlineTagsDoc: DocContent = run {
            var text = docContent

            var i = 0
            while (true) {
                val inlineTagNames = text
                    .findInlineTagNamesInDocContentWithRanges()
                    .filter { (tagName, _) -> tagIsSupported(tagName) }

                if (inlineTagNames.isEmpty()) break

                var wasModified = false
                for ((_, range) in inlineTagNames) {
                    val tagContent = text.substring(range)

                    // sanity check
                    require(tagContent.startsWith("{@") && tagContent.endsWith("}")) {
                        "Tag content must start with '{@' and end with '}'"
                    }

                    val newTagContent = try {
                        processInlineTagWithContent(
                            tagWithContent = tagContent,
                            path = path,
                            documentable = documentable,
                        )
                    } catch (e: Throwable) {
                        throw TagDocProcessorFailedException(
                            processorName = name,
                            documentable = documentable,
                            currentDoc = text,
                            rangeInCurrentDoc = range,
                            currentTagContent = tagContent,
                            cause = e,
                        )
                    }
                    text = text.replaceRange(range, newTagContent)

                    wasModified = tagContent != newTagContent

                    // Restart the loop since ranges might have changed,
                    // continue if there were no modifications
                    if (wasModified) break
                }

                // while true safety check
                val shouldContinue = shouldContinue(
                    i = i++,
                    anyModifications = wasModified,
                    parameters = parameters,
                )
                if (!shouldContinue) break
            }

            return@run text
        }

        // Then process the normal tags
        val processedDoc: DocContent = processedInlineTagsDoc
            .splitDocContentPerBlockWithRanges()
            .map { (split, rangeDocContent) ->
                val shouldProcess =
                    split.trimStart().startsWith("@") &&
                            split.getTagNameOrNull()
                                ?.let(::tagIsSupported) == true

                if (shouldProcess) {
                    try {
                        processBlockTagWithContent(
                            tagWithContent = split,
                            path = path,
                            documentable = documentable,
                        )
                    } catch (e: Throwable) {
                        throw TagDocProcessorFailedException(
                            processorName = name,
                            documentable = documentable,
                            currentDoc = processedInlineTagsDoc,
                            rangeInCurrentDoc = rangeDocContent,
                            currentTagContent = split,
                            cause = e,
                        )
                    }
                } else {
                    split
                }
            }.let { list ->
                // skip empty blocks, making sure to keep the first and last blocks
                // even if they are empty, to preserve original newlines
                list.filterIndexed { i, it ->
                    i == 0 || i == list.lastIndex || it.isNotEmpty()
                }
            }
            .joinToString("\n")

        return processedDoc
    }
}

/**
 * This exception is thrown when a [TagDocProcessor] fails to process a tag.
 * It contains the current state of the doc, the range of the tag that failed to process,
 * and the content of the tag that failed to process.
 */
open class TagDocProcessorFailedException(
    processorName: String,
    documentable: DocumentableWrapper,
    currentDoc: DocContent,
    rangeInCurrentDoc: IntRange,
    currentTagContent: DocContent,
    cause: Throwable? = null,
) : DocProcessorFailedException(
    processorName = processorName,
    cause = cause,
    message = buildString {
        val rangeInFile = documentable.docTextRange
        val fileText = documentable.file.readText()
        val (line, char) = fileText.getLineAndCharacterOffset(rangeInFile.start)

        appendLine("Doc processor $processorName failed processing doc:")
        appendLine("(${documentable.file.absolutePath}:$line:$char)")
        appendLine("\u200E")
        appendLine("Current state of the doc with the <a href=\"\">cause for the exception</a>:")
        appendLine("--------------------------------------------------")
        appendLine(
            currentDoc
                .replaceRange(
                    range = rangeInCurrentDoc,
                    replacement = "<a href=\"\">${currentDoc.substring(rangeInCurrentDoc)}</a>",
                )
                .toDoc()
        )
        appendLine("--------------------------------------------------")
        cause?.message?.let { appendLine(it) }
    },
)