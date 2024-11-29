package nl.jolanrensen.docProcessor

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

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

    protected lateinit var mutableDocumentablesByPath: MutableDocumentablesByPath

    /**
     * Allows you to access the documentables to be processed as well as to gain
     * the ability to query for any documentable in the source.
     */
    val documentablesByPath: DocumentablesByPath
        get() = mutableDocumentablesByPath

    /**
     * Optionally, you can filter the [DocumentableWrapper]s that will appear in
     * [processBlockTagWithContent] and [processInlineTagWithContent] as `filteredDocumentables`.
     * This can be useful as an optimization.
     *
     * This filter will also be applied to all the docs that are processed. Normally, all docs with
     * a supported tag will be processed. If you want to filter them more strictly, override this method too.
     */
    open fun <T : DocumentableWrapper> filterDocumentablesToProcess(documentable: T): Boolean = true

    open fun <T : DocumentableWrapper> filterDocumentablesToQuery(documentable: T): Boolean = true

    /**
     * You can optionally sort the documentables to optimize the order in which they are processed.
     * [TagDocAnalyser] can be used for that.
     */
    open fun <T : DocumentableWrapper> sortDocumentables(
        documentables: List<T>,
        processLimit: Int,
        documentablesByPath: DocumentablesByPath,
    ): Iterable<T> = documentables

    /**
     * Whether this processor can process multiple documentables in parallel.
     * If `true`, the processor will be run in parallel for each documentable.
     * If `false`, the processor will be run sequentially for each documentable.
     */
    open val canProcessParallel: Boolean = true

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
     * @param [processLimit] The process limit from [ProcessDocsAction.Parameters].
     * @return `true` if recursion limit is reached, `false` otherwise.
     */
    @Throws(IllegalStateException::class)
    open fun shouldContinue(i: Int, anyModifications: Boolean, processLimit: Int): Boolean {
        val processLimitReached = i >= processLimit

        val hasSupportedTags = mutableDocumentablesByPath
            .documentablesToProcess
            .any { it.value.any { it.hasSupportedTag } }
        val atLeastOneRun = i > 0
        val tagsArePresentButNoModifications = hasSupportedTags && !anyModifications && atLeastOneRun

        // Throw error if process limit is reached or if supported tags keep being present but no modifications are made
        if (processLimitReached || tagsArePresentButNoModifications) {
            onProcessError()
        }

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
     * @param [documentable] The Documentable belonging to the current tag.
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
    override fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath {
        // Convert to mutable
        mutableDocumentablesByPath = documentablesByPath
            .toMutable()
            .withQueryFilter(::filterDocumentablesToQuery)
            .withDocsToProcessFilter(::filterDocumentablesToProcess)

        // Main recursion loop that will continue until all supported tags are replaced
        // or the process limit is reached.
        var i = 0
        while (true) {
            val filteredDocumentablesWithTag = mutableDocumentablesByPath
                .documentablesToProcess
                .flatMap { it.value }
                .filter { it.hasSupportedTag }
                .distinctBy { it.identifier }
                .let { sortDocumentables(it, processLimit, documentablesByPath) }

            var anyModifications = false
            if (canProcessParallel) {
                runBlocking {
                    anyModifications = filteredDocumentablesWithTag
                        .mapNotNull { documentable ->
                            if (!documentable.hasSupportedTag) {
                                null
                            } else {
                                async {
                                    processDocumentable(documentable, processLimit)
                                }
                            }
                        }.awaitAll()
                        .any { it }
                }
            } else {
                for (documentable in filteredDocumentablesWithTag) {
                    if (!documentable.hasSupportedTag) continue
                    anyModifications = processDocumentable(documentable, processLimit)
                }
            }

            val shouldContinue = shouldContinue(
                i = i++,
                anyModifications = anyModifications,
                processLimit = processLimit,
            )
            if (!shouldContinue) break
        }

        return mutableDocumentablesByPath
    }

    protected fun processDocumentable(documentable: MutableDocumentableWrapper, processLimit: Int): Boolean {
        val docContent = documentable.docContent
        val processedDoc = processTagsInContent(
            docContent = docContent,
            path = documentable.fullyQualifiedPath,
            documentable = documentable,
            processLimit = processLimit,
        )

        val wasModified = docContent != processedDoc
        if (wasModified) {
            documentable.modifyDocContentAndUpdate(processedDoc)
        }
        return wasModified
    }

    private fun processTagsInContent(
        docContent: DocContent,
        path: String,
        documentable: MutableDocumentableWrapper,
        processLimit: Int,
    ): DocContent {
        logger.info { "Processing inline tags in doc at '$path'" }

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
                    processLimit = processLimit,
                )
                if (!shouldContinue) break
            }

            return@run text
        }

        logger.info { "Processing block tags in doc at '$path'" }

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
            }.joinToString("\n")

        return processedDoc
    }

    fun getArgumentHighlightForOrNull(
        argumentIndex: Int,
        rangeInDocText: IntRange,
        docText: String,
        tagName: String,
        numberOfArguments: Int,
        type: HighlightType,
    ): HighlightInfo? {
        val (_, rangeInSubstring) = docText.substring(rangeInDocText).getTagArgumentWithRangeByIndexOrNull(
            index = argumentIndex,
            tag = tagName,
            numberOfArguments = numberOfArguments,
        ) ?: return null
        return HighlightInfo(
            range = rangeInSubstring.first + rangeInDocText.first..rangeInSubstring.last + rangeInDocText.first,
            type = type,
        )
    }

    protected open fun getHighlightsForInlineTag(
        tagName: String,
        rangeInDocText: IntRange,
        docText: String,
    ): List<HighlightInfo> =
        buildList {
            // Left '{'
            val leftBracket = HighlightInfo(
                range = rangeInDocText.first..rangeInDocText.first,
                type = HighlightType.BRACKET,
            )

            // '@' and tag name
            this += HighlightInfo(
                range = (rangeInDocText.first + 1)..(rangeInDocText.first + 1 + tagName.length),
                type = HighlightType.TAG,
            )

            // Right '}'
            val rightBracket = HighlightInfo(
                range = rangeInDocText.last..rangeInDocText.last,
                type = HighlightType.BRACKET,
            )

            // Linking brackets
            this += leftBracket.copy(related = listOf(rightBracket))
            this += rightBracket.copy(related = listOf(leftBracket))
        }

    protected open fun getHighlightsForBlockTag(
        tagName: String,
        docContentRangesInDocText: List<IntRange>,
        docText: String,
    ): List<HighlightInfo> =
        buildList {
            // '@' and tag name
            val firstLineDocContentRange = docContentRangesInDocText.first()
            this += HighlightInfo(
                range = firstLineDocContentRange.first..(firstLineDocContentRange.first + 1 + tagName.length),
                type = HighlightType.TAG,
            )
        }

    override fun getHighlightsFor(docText: String): List<HighlightInfo> =
        buildList {
            // {@inline tags} // TODO does not handle multiple lines well, includes * chars
            val inlineTags = docText.findInlineTagNamesInDocTextWithRanges()
            for ((tagName, range) in inlineTags) {
                if (!tagIsSupported(tagName)) continue
                this += getHighlightsForInlineTag(tagName, range, docText)
            }

            // @block tags TODO cannot find block tags in doc text yet
//            val blockTags = docText.findBlockTagsInDocTextWithDocContentRanges()
//            for ((tagName, docContentRanges) in blockTags) {
//                if (!tagIsSupported(tagName)) continue
//
//                getHighlightsForBlockTag(tagName, docContentRanges, docText)
//            }
        }
}
