package nl.jolanrensen.docProcessor

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


    private lateinit var mutableDocumentablesByPath: MutableDocumentablesByPath

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
    open fun shouldContinue(
        i: Int,
        anyModifications: Boolean,
        processLimit: Int,
    ): Boolean {
        val processLimitReached = i >= processLimit

        val hasSupportedTags = mutableDocumentablesByPath
            .documentablesToProcess
            .any { it.value.any { it.hasSupportedTag } }
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
    override fun process(
        processLimit: Int,
        documentablesByPath: DocumentablesByPath,
    ): DocumentablesByPath {
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
                        processLimit = processLimit,
                    )

                    val wasModified = docContent != processedDoc
                    if (wasModified) {
                        anyModifications = true
                        documentable.modifyDocContentAndUpdate(processedDoc)
                    }
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
    val documentable: DocumentableWrapper,
    val currentDoc: DocContent,
    val rangeInCurrentDoc: IntRange,
    cause: Throwable? = null,
) : DocProcessorFailedException(
    processorName = processorName,
    cause = cause,
    message = renderMessage(
        documentable = documentable,
        rangeInCurrentDoc = rangeInCurrentDoc,
        processorName = processorName,
        currentDoc = currentDoc,
        cause = cause,
    ),
) {
    companion object {
        private fun renderMessage(
            documentable: DocumentableWrapper,
            rangeInCurrentDoc: IntRange,
            processorName: String,
            currentDoc: DocContent,
            cause: Throwable?,
        ): String = buildString {
            val docRangeInFile = documentable.docFileTextRange
            val fileText = documentable.file.readText()
            val (docLine, docChar) = fileText.getLineAndCharacterOffset(docRangeInFile.first)
            val (exceptionLine, exceptionChar) = fileText.getLineAndCharacterOffset(rangeInCurrentDoc.first)

            fun highlightException(it: String) = "<a href=\"\">$it</a>"

            appendLine("Doc processor $processorName failed processing doc:")
            appendLine("Doc location: ${documentable.file.absolutePath}:$docLine:$docChar")
            appendLine("Exception location: ${documentable.file.absolutePath}:$exceptionLine:$exceptionChar")
            appendLine(
                "Tag throwing the exception: ${
                    highlightException(
                        currentDoc.substring(
                            rangeInCurrentDoc.coerceAtMost(
                                currentDoc.lastIndex
                            )
                        )
                    )
                }"
            )
            cause?.message?.let {
                appendLine("Reason for the exception: $it")
            }
            appendLine("\u200E")
            appendLine("Current state of the doc with the ${highlightException("cause for the exception")}:")
            appendLine("--------------------------------------------------")
            appendLine(
                try {
                    currentDoc.replaceRange(
                        range = rangeInCurrentDoc.coerceAtMost(currentDoc.lastIndex),
                        replacement = highlightException(currentDoc.substring(rangeInCurrentDoc.coerceAtMost(currentDoc.lastIndex))),
                    )
                } catch (e: Throwable) {
                    currentDoc
                }.toDoc()
            )
            appendLine("--------------------------------------------------")
        }
    }

    fun renderMessage(): String =
        renderMessage(
            documentable = documentable,
            rangeInCurrentDoc = rangeInCurrentDoc,
            processorName = processorName,
            currentDoc = currentDoc,
            cause = cause,
        )

    fun renderDoc(): String = buildString {
        val docRangeInFile = documentable.docFileTextRange
        val fileText = documentable.file.readText()
        val (docLine, docChar) = fileText.getLineAndCharacterOffset(docRangeInFile.first)
        val (exceptionLine, exceptionChar) = fileText.getLineAndCharacterOffset(rangeInCurrentDoc.first)

        fun highlightException(it: String) = "!!!$it!!!"

        val indent = "&nbsp;&nbsp;&nbsp;&nbsp;"
        val lineBreak = "\n$indent\n"

        appendLine("# Error in DocProcessor")
        appendLine("## Doc processor $processorName failed processing doc.")
        appendLine()
        appendLine("### Doc location:")
        appendLine()
        appendLine("`${documentable.file.path}:$docLine:$docChar`")
        appendLine(lineBreak)
        appendLine("### Exception location:")
        appendLine()
        appendLine("`${documentable.file.absolutePath}:$exceptionLine:$exceptionChar`")
        appendLine(lineBreak)
        appendLine("### Tag throwing the exception:")
        appendLine()
        appendLine("**`" + currentDoc.substring(rangeInCurrentDoc) + "`**")
        appendLine(lineBreak)
        appendLine("### Reason for the exception:")
        appendLine()
        cause?.message?.let {
            for (line in it.lines()) {
                appendLine(line)
                appendLine()
            }
        }
        appendLine(lineBreak)
        appendLine("Current state of the doc with the `${highlightException("cause for the exception")}`:")
        appendLine()
        appendLine("--------------------------------------------------")
        appendLine("```")
        appendLine(
            try {
                currentDoc.replaceRange(
                    range = rangeInCurrentDoc,
                    replacement = highlightException(currentDoc.substring(rangeInCurrentDoc)),
                )
            } catch (e: Throwable) {
                currentDoc
            }.toDoc()
        )
        appendLine("```")
        appendLine("--------------------------------------------------")
    }
}