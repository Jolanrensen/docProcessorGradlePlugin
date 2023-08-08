package nl.jolanrensen.docProcessor

open class DocumentablesByPathFromMap(
    private val allDocs: Map<String, List<DocumentableWrapper>>,
    override val queryFilter: DocumentableWrapperFilter = NO_FILTER,
    override val documentablesToProcessFilter: DocumentableWrapperFilter = NO_FILTER,
) : DocumentablesByPath {

    override val documentablesToProcess: Map<String, List<DocumentableWrapper>> = allDocs
        .mapValues { (_, documentables) ->
            documentables.filter(documentablesToProcessFilter)
        }

    private val docsToQuery: Map<String, List<DocumentableWrapper>> = allDocs
        .mapValues { (_, documentables) ->
            documentables.filter(queryFilter)
        }

    override fun query(path: String): List<DocumentableWrapper>? = docsToQuery[path]

    @Suppress("UNCHECKED_CAST")
    override fun toMutable(): MutableDocumentablesByPath =
        this as? MutableDocumentablesByPath ?: MutableDocumentablesByPathFromMap(
            allDocs = allDocs.toMutable(),
            queryFilter = queryFilter,
            documentablesToProcessFilter = documentablesToProcessFilter,
        )

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): DocumentablesByPath =
        DocumentablesByPathFromMap(
            allDocs = allDocs,
            queryFilter = queryFilter,
            documentablesToProcessFilter = documentablesToProcessFilter,
        )

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): DocumentablesByPath =
        DocumentablesByPathFromMap(
            allDocs = allDocs,
            queryFilter = queryFilter,
            documentablesToProcessFilter = docsToProcessFilter,
        )

    override fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): DocumentablesByPath =
        DocumentablesByPathFromMap(
            allDocs = allDocs,
            queryFilter = queryFilter,
            documentablesToProcessFilter = docsToProcessFilter,
        )
}

class MutableDocumentablesByPathFromMap(
    private val allDocs: Map<String, List<MutableDocumentableWrapper>>,
    override val queryFilter: DocumentableWrapperFilter = NO_FILTER,
    override val documentablesToProcessFilter: DocumentableWrapperFilter = NO_FILTER,
) : MutableDocumentablesByPath {

    override val documentablesToProcess: Map<String, List<MutableDocumentableWrapper>> = allDocs
        .mapValues { (_, documentables) ->
            documentables.filter(documentablesToProcessFilter)
        }

    private val docsToQuery: Map<String, List<MutableDocumentableWrapper>> = allDocs
        .mapValues { (_, documentables) ->
            documentables.filter(documentablesToProcessFilter)
        }

    override fun query(path: String): List<MutableDocumentableWrapper>? = docsToQuery[path]

    override fun toMutable(): MutableDocumentablesByPath = this

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        MutableDocumentablesByPathFromMap(
            allDocs = allDocs,
            queryFilter = queryFilter,
            documentablesToProcessFilter = documentablesToProcessFilter,
        )

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        MutableDocumentablesByPathFromMap(
            allDocs = allDocs,
            queryFilter = queryFilter,
            documentablesToProcessFilter = docsToProcessFilter,
        )

    override fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): MutableDocumentablesByPath =
        MutableDocumentablesByPathFromMap(
            allDocs = allDocs,
            queryFilter = queryFilter,
            documentablesToProcessFilter = docsToProcessFilter,
        )
}