package nl.jolanrensen.docProcessor

import kotlin.jvm.Throws

abstract class TagDocProcessor : DocProcessor {

    /** The tags to be replaced, like "sample" */
    abstract fun tagIsSupported(tag: String): Boolean

    /** Returns true if [tagIsSupported] is in this [DocumentableWithSource]. */
    val DocumentableWithSource.hasASupportedTag
        get() = tags.any(::tagIsSupported)

    /**
     * Converts a [Map]<[String], [List]<[DocumentableWithSource]>> to
     * [Map]<[String], [List]<[MutableDocumentableWithSource]>>.
     *
     * The [MutableDocumentableWithSource] is a copy of the original [DocumentableWithSource].
     */
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, List<DocumentableWithSource>>.toMutable(): Map<String, List<MutableDocumentableWithSource>> =
        mapValues { (_, documentables) ->
            if (documentables.all { it is MutableDocumentableWithSource }) {
                documentables as List<MutableDocumentableWithSource>
            } else {
                documentables.map { it.asMutable() }
            }
        }

    /**
     * Optionally, you can filter the [DocumentableWithSource]s that will appear in [processTagWithContent].
     * By default, all documentables are filtered to contain a supported tag.
     * Override if you want to filter them differently.
     */
    open fun <T : DocumentableWithSource> filterDocumentables(documentable: T): Boolean =
        documentable.hasASupportedTag

    /**
     * Provide a more meaningful error message when the given process limit is reached.
     *
     * @param filteredDocumentables Documentables filtered by [filterDocumentables]
     * @param allDocumentables All documentables
     * @return [Nothing] must throw an error
     * @throws [IllegalStateException] On proces limit reached
     */
    @Throws(IllegalStateException::class)
    open fun onProcesLimitReached(
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>,
    ): Nothing = error("Process limit reached")


    /**
     * Process a tag with content ([tagWithContent]) into whatever you like.
     *
     * @param tagWithContent Tag with content to process.
     *  For example: `@tag Some content`
     *  Can contain newlines and does include tag.
     * @param path The path of the doc where the tag is found.
     * @param documentable The Documentable beloning to the current tag.
     * @param docContent The content of the entire doc beloning to the current tag.
     * @param filteredDocumentables Filtered documentables by [filterDocumentables].
     *   The [DocumentableWithSource]s in this map are the same objects as in [allDocumentables], just a subset.
     * @param allDocumentables All documentables in the source set.
     *
     * @return A [String] that will replace the tag with content entirely.
     */
    abstract fun processTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>,
    ): String


    /**
     * This is the main function that will be called by the [DocProcessor].
     * It will process all documentables found in the source set and replace all
     * supported tags ([tagIsSupported]) and their content with the result of [processTagWithContent].
     * The processing will continue until there are no more supported tags found in all
     * filtered documentables. This means that recursion (a.k.a tags that create other supported tags)
     * is possible. However, there is a limit to prevent infinite recursion ([ProcessDocsAction.Parameters.processLimit]).
     */
    override fun process(
        parameters: ProcessDocsAction.Parameters,
        documentablesByPath: Map<String, List<DocumentableWithSource>>,
    ): Map<String, List<DocumentableWithSource>> {
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
        while (filteredDocumentables.any { it.value.any { it.hasASupportedTag } }) {

            if (i++ > parameters.processLimit)
                onProcesLimitReached(filteredDocumentables, allDocumentables)

            val filteredDocumentablesWithTag = filteredDocumentables
                .filter { it.value.any { it.hasASupportedTag } }
            for ((path, documentables) in filteredDocumentablesWithTag) {
                for (documentable in documentables) {
                    if (!documentable.hasASupportedTag) continue

                    val docContent = documentable.docContent
                    val processedDoc = docContent
                        .splitDocContent()
                        .joinToString("\n") { split ->
                            val shouldProcess = split
                                .getTagNameOrNull()
                                ?.let(::tagIsSupported) == true

                            if (shouldProcess) {
                                processTagWithContent(
                                    tagWithContent = split,
                                    path = path,
                                    documentable = documentable,
                                    docContent = docContent,
                                    filteredDocumentables = filteredDocumentables,
                                    allDocumentables = allDocumentables,
                                )
                            } else {
                                split
                            }
                        }

                    val wasModified = docContent != processedDoc
                    if (wasModified) {
                        val tags = processedDoc
                            .splitDocContent()
                            .mapNotNull { it.getTagNameOrNull() }
                            .toSet()

                        documentable.apply {
                            this.docContent = processedDoc
                            this.tags = tags
                            this.isModified = true
                        }
                    }
                }
            }
        }

        return allDocumentables
    }
}