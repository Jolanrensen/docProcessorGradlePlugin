package nl.jolanrensen.docProcessor

open class DocumentablesByPathWithCache(
    private val unfilteredDocsToProcess: Map<String, List<DocumentableWrapper>>,
    private val query: (String) -> List<DocumentableWrapper>?,
    override val queryFilter: DocumentableWrapperFilter = NO_FILTER,
    override val documentablesToProcessFilter: DocumentableWrapperFilter = NO_FILTER,
) : DocumentablesByPath {

    override val documentablesToProcess: Map<String, List<DocumentableWrapper>> =
        when (documentablesToProcessFilter) {
            NO_FILTER -> unfilteredDocsToProcess
            else -> unfilteredDocsToProcess
                .mapValues { (_, documentables) ->
                    documentables.filter(documentablesToProcessFilter)
                }
        }

    private val queryCache: MutableMap<String, List<DocumentableWrapper>> = mutableMapOf()

    override fun query(path: String): List<DocumentableWrapper>? =
        queryCache.getOrPut(path) { // should return null in SingleColumn.all case
            (unfilteredDocsToProcess[path] ?: query.invoke(path))
                ?.filter(queryFilter)
                ?: return@query null
        }

    override fun toMutable(): MutableDocumentablesByPath =
        MutableDocumentablesByPathWithCache(
            unfilteredDocsToProcess = unfilteredDocsToProcess.toMutable(),
            query = { query(it)?.map { it.toMutable() } },
            queryFilter = queryFilter,
            documentablesToProcessFilter = documentablesToProcessFilter,
        )

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): DocumentablesByPath =
        when {
            queryFilter == this.queryFilter -> this
            else -> DocumentablesByPathWithCache(
                unfilteredDocsToProcess = unfilteredDocsToProcess,
                query = query,
                queryFilter = queryFilter,
                documentablesToProcessFilter = documentablesToProcessFilter,
            )
        }

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): DocumentablesByPath =
        when {
            docsToProcessFilter == this.documentablesToProcessFilter -> this
            else -> DocumentablesByPathWithCache(
                unfilteredDocsToProcess = unfilteredDocsToProcess,
                query = query,
                queryFilter = queryFilter,
                documentablesToProcessFilter = docsToProcessFilter,
            )
        }

    override fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): DocumentablesByPath =
        when {
            queryFilter == this.queryFilter && docsToProcessFilter == this.documentablesToProcessFilter -> this
            else -> DocumentablesByPathWithCache(
                unfilteredDocsToProcess = unfilteredDocsToProcess,
                query = query,
                queryFilter = queryFilter,
                documentablesToProcessFilter = docsToProcessFilter,
            )
        }
}

class MutableDocumentablesByPathWithCache(
    private val unfilteredDocsToProcess: Map<String, List<MutableDocumentableWrapper>>,
    private val query: (String) -> List<MutableDocumentableWrapper>?,
    override val queryFilter: DocumentableWrapperFilter = NO_FILTER,
    override val documentablesToProcessFilter: DocumentableWrapperFilter = NO_FILTER,
) : MutableDocumentablesByPath {

    override val documentablesToProcess: Map<String, List<MutableDocumentableWrapper>> =
        when (documentablesToProcessFilter) {
            NO_FILTER -> unfilteredDocsToProcess
            else -> unfilteredDocsToProcess
                .mapValues { (_, documentables) ->
                    documentables.filter(documentablesToProcessFilter)
                }
        }

    private val queryCache: MutableMap<String, List<MutableDocumentableWrapper>> = mutableMapOf()

    override fun query(path: String): List<MutableDocumentableWrapper>? =
        queryCache.getOrPut(path) {
            (unfilteredDocsToProcess[path] ?: query.invoke(path))
                ?.filter(queryFilter)
                ?: return@query null
        }

    override fun toMutable(): MutableDocumentablesByPath = this

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        when {
            queryFilter == this.queryFilter -> this
            else -> MutableDocumentablesByPathWithCache(
                unfilteredDocsToProcess = unfilteredDocsToProcess,
                query = query,
                queryFilter = queryFilter,
                documentablesToProcessFilter = documentablesToProcessFilter,
            )
        }

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        when {
            docsToProcessFilter == this.documentablesToProcessFilter -> this
            else -> MutableDocumentablesByPathWithCache(
                unfilteredDocsToProcess = unfilteredDocsToProcess,
                query = query,
                queryFilter = queryFilter,
                documentablesToProcessFilter = docsToProcessFilter,
            )
        }

    override fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): MutableDocumentablesByPath =
        when {
            queryFilter == this.queryFilter && docsToProcessFilter == this.documentablesToProcessFilter -> this
            else -> MutableDocumentablesByPathWithCache(
                unfilteredDocsToProcess = unfilteredDocsToProcess,
                query = query,
                queryFilter = queryFilter,
                documentablesToProcessFilter = docsToProcessFilter,
            )
        }
}