package nl.jolanrensen.docProcessor

/**
 * Specific [DocProcessor] that processes just tags.
 *
 * Extending classes need to define which tags they can handle in [tagIsSupported],
 * how they handle block tags and inline tags in [processBlockTagWithContent] and [processInlineTagWithContent],
 * respectively.
 *
 * [filterDocumentables] can be overridden to change the value of `filteredDocumentables` in
 * [processBlockTagWithContent] and [processInlineTagWithContent].
 * By default, it filters out all documentables that do not have a supported tag.
 *
 * [onProcesError] can be overridden to change the error message when the process limit is reached
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
    val DocumentableWrapper.hasASupportedTag
        get() = tags.any(::tagIsSupported)

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
     * Optionally, you can filter the [DocumentableWrapper]s that will appear in [processBlockTagWithContent].
     * By default, all documentables are filtered to contain a supported tag.
     * Override if you want to filter them differently.
     */
    open fun <T : DocumentableWrapper> filterDocumentables(documentable: T): Boolean =
        documentable.hasASupportedTag

    /**
     * Provide a meaningful error message when the given process limit is reached.
     *
     * @param filteredDocumentables Documentables filtered by [filterDocumentables]
     * @param allDocumentables All documentables
     * @return [Nothing] must throw an error
     * @throws [IllegalStateException] On proces limit reached
     */
    @Throws(IllegalStateException::class)
    open fun onProcesError(
        filteredDocumentables: Map<String, List<DocumentableWrapper>>,
        allDocumentables: Map<String, List<DocumentableWrapper>>,
    ): Nothing = error("Process limit reached")

    /**
     * Check the recursion limit to break the while loop.
     * By default, it will call [onProcesError] if the process limit (defined in [ProcessDocsAction.Parameters.processLimit])
     * is reached and if supported tags are present but no modifications are made (indicating an infinite loop).
     *
     * @param i The current iteration.
     * @param anyModifications `true` if any modifications were made, `false` otherwise.
     * @param parameters The [ProcessDocsAction.Parameters] used to process the docs.
     * @param filteredDocumentables Filtered documentables by [filterDocumentables], which, by default, filters the
     * documentables to have a supported tag in them. The [DocumentableWrapper]s in this map are the same objects as in [allDocumentables], just a subset.
     * @param allDocumentables All documentables in the source set.
     * @return `true` if recursion limit is reached, `false` otherwise.
     */
    @Throws(IllegalStateException::class)
    open fun shouldContinue(
        i: Int,
        anyModifications: Boolean,
        parameters: ProcessDocsAction.Parameters,
        filteredDocumentables: Map<String, List<DocumentableWrapper>>,
        allDocumentables: Map<String, List<DocumentableWrapper>>,
    ): Boolean {
        val hasSupportedTags = filteredDocumentables.any { it.value.any { it.hasASupportedTag } }
        if ((i > 0 && hasSupportedTags && !anyModifications) || i >= parameters.processLimit)
            onProcesError(filteredDocumentables, allDocumentables)

        return hasSupportedTags
    }

    /**
     * Process a block tag with content ([tagWithContent]) into whatever you like.
     *
     * @param tagWithContent Tag with content block to process.
     *  For example: `@tag Some content`
     *  Can contain newlines, and does include tag.
     *  The block continues until the next tag block starts.
     * @param path The path of the doc where the tag is found.
     * @param documentable The Documentable beloning to the current tag.
     * @param filteredDocumentables Filtered documentables by [filterDocumentables], which, by default, filters the
     *  documentables to have a supported tag in them. The [DocumentableWrapper]s in this map are the same objects as in [allDocumentables], just a subset.
     * @param allDocumentables All documentables in the source set.
     *
     * @return A [String] that will replace the tag with content entirely.
     */
    abstract fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
        filteredDocumentables: Map<String, List<DocumentableWrapper>>,
        allDocumentables: Map<String, List<DocumentableWrapper>>,
    ): String

    /**
     * Process an inline tag with content ([tagWithContent]) into whatever you like.
     *
     * @param tagWithContent Tag with content to process.
     *  For example: `{@tag Some content}`
     *  Contains {} and tag.
     * @param path The path of the doc where the tag is found.
     * @param documentable The Documentable beloning to the current tag.
     * @param filteredDocumentables Filtered documentables by [filterDocumentables], which, by default, filters the
     *  documentables to have a supported tag in them. The [DocumentableWrapper]s in this map are the same objects as in [allDocumentables], just a subset.
     * @param allDocumentables All documentables in the source set.
     *
     * @return A [String] that will replace the tag with content entirely.
     */
    abstract fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
        filteredDocumentables: Map<String, List<DocumentableWrapper>>,
        allDocumentables: Map<String, List<DocumentableWrapper>>,
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
        val allDocumentables = documentablesByPath.toMutable()

        // Filter documentables
        val filteredDocumentables = allDocumentables
            .mapValues { (_, documentables) ->
                documentables.filter(::filterDocumentables)
            }.filterValues { it.isNotEmpty() }

        // Main recursion loop that will continue until all supported tags are replaced
        // or the process limit is reached.
        var i = 0
        while (true) {
            val filteredDocumentablesWithTag = filteredDocumentables
                .filter { it.value.any { it.hasASupportedTag } }

            var anyModifications = false
            for ((path, documentables) in filteredDocumentablesWithTag) {
                for (documentable in documentables) {
                    if (!documentable.hasASupportedTag) continue

                    val docContent = documentable.docContent
                    val processedDoc = processTagsInContent(
                        docContent = docContent,
                        path = path,
                        documentable = documentable,
                        filteredDocumentables = filteredDocumentables,
                        allDocumentables = allDocumentables,
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
                filteredDocumentables = filteredDocumentables,
                allDocumentables = allDocumentables,
            )
            if (!shouldContinue) break
        }

        return allDocumentables
    }

    private fun processTagsInContent(
        docContent: DocContent,
        path: String,
        documentable: MutableDocumentableWrapper,
        filteredDocumentables: Map<String, List<MutableDocumentableWrapper>>,
        allDocumentables: Map<String, List<MutableDocumentableWrapper>>,
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

                    val newTagContent = processInlineTagWithContent(
                        tagWithContent = tagContent,
                        path = path,
                        documentable = documentable,
                        filteredDocumentables = filteredDocumentables,
                        allDocumentables = allDocumentables,
                    )
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
                    filteredDocumentables = filteredDocumentables,
                    allDocumentables = allDocumentables,
                )
                if (!shouldContinue) break
            }

            return@run text
        }

        // Then process the normal tags
        val processedDoc: DocContent = processedInlineTagsDoc
            .splitDocContentPerBlock()
            .joinToString("\n") { split ->
                val shouldProcess =
                    split.trimStart().startsWith("@") &&
                            split.getTagNameOrNull()
                                ?.let(::tagIsSupported) == true

                if (shouldProcess) {
                    processBlockTagWithContent(
                        tagWithContent = split,
                        path = path,
                        documentable = documentable,
                        filteredDocumentables = filteredDocumentables,
                        allDocumentables = allDocumentables,
                    )
                } else {
                    split
                }
            }

        return processedDoc
    }
}