package nl.jolanrensen.docProcessor

import java.util.*

typealias DocumentableWrapperFilter = (DocumentableWrapper) -> Boolean

interface DocumentablesByPath {

    val queryFilter: DocumentableWrapperFilter

    val documentablesToProcessFilter: DocumentableWrapperFilter

    val documentablesToProcess: Map<String, List<DocumentableWrapper>>

    /**
     * Returns a list of [DocumentableWrapper]s for the given [path].
     *
     * Returns empty list if [path] exists in the project
     * but no [DocumentableWrapper] is found.
     *
     * Returns `null` if no [DocumentableWrapper] is found for the given [path] and [path]
     * does not exist in the project.
     */
    fun query(path: String): List<DocumentableWrapper>?

    operator fun get(path: String): List<DocumentableWrapper>? = query(path)

    operator fun get(identifier: UUID): DocumentableWrapper? =
        documentablesToProcess
            .values
            .firstNotNullOfOrNull { it.firstOrNull { it.identifier == identifier } }

    fun toMutable(): MutableDocumentablesByPath

    fun withQueryFilter(queryFilter: DocumentableWrapperFilter): DocumentablesByPath

    fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): DocumentablesByPath

    fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): DocumentablesByPath

    companion object {
        val EMPTY: DocumentablesByPath = DocumentablesByPathFromMap(emptyMap())

        fun of(map: Map<String, List<DocumentableWrapper>>): DocumentablesByPath = DocumentablesByPathFromMap(map)
        fun of(map: Map<String, List<MutableDocumentableWrapper>>): MutableDocumentablesByPath =
            MutableDocumentablesByPathFromMap(map)
    }
}

fun <T : DocumentablesByPath> T.withoutFilters(): T = this
    .withFilters({ true }, { true }) as T

fun Map<String, List<DocumentableWrapper>>.toDocumentablesByPath(): DocumentablesByPath = DocumentablesByPath.of(this)
fun Iterable<Pair<String, List<DocumentableWrapper>>>.toDocumentablesByPath(): DocumentablesByPath =
    toMap().toDocumentablesByPath()

interface MutableDocumentablesByPath : DocumentablesByPath {

    override fun query(path: String): List<MutableDocumentableWrapper>?

    override operator fun get(path: String): List<MutableDocumentableWrapper>? = query(path)

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): MutableDocumentablesByPath

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): MutableDocumentablesByPath

    override fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): MutableDocumentablesByPath


    override val documentablesToProcess: Map<String, List<MutableDocumentableWrapper>>

    override fun toMutable(): MutableDocumentablesByPath = this
}

// region implementations

open class DocumentablesByPathFromMap(
    private val allDocs: Map<String, List<DocumentableWrapper>>,
    override val queryFilter: DocumentableWrapperFilter = { true },
    override val documentablesToProcessFilter: DocumentableWrapperFilter = { true },
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
    override val queryFilter: DocumentableWrapperFilter = { true },
    override val documentablesToProcessFilter: DocumentableWrapperFilter = { true },
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

open class DocumentablesByPathWithCache(
    private val unfilteredDocsToProcess: Map<String, List<DocumentableWrapper>>,
    private val query: (String) -> List<DocumentableWrapper>?,
    override val queryFilter: DocumentableWrapperFilter = { true },
    override val documentablesToProcessFilter: DocumentableWrapperFilter = { true },
) : DocumentablesByPath {

    override val documentablesToProcess: Map<String, List<DocumentableWrapper>> =
        unfilteredDocsToProcess
            .mapValues { (_, documentables) ->
                documentables.filter(documentablesToProcessFilter)
            }

    private val queryCache: MutableMap<String, List<DocumentableWrapper>> = mutableMapOf()

    override fun query(path: String): List<DocumentableWrapper>? =
        queryCache.getOrPut(path) { // should return null in SingleColumn.all case
            (unfilteredDocsToProcess[path] ?: query.invoke(path))
                ?.filter(queryFilter)
                ?: return@query null
        }

    @Suppress("UNCHECKED_CAST")
    override fun toMutable(): MutableDocumentablesByPath = MutableDocumentablesByPathWithCache(
        unfilteredDocsToProcess = unfilteredDocsToProcess.toMutable(),
        query = { query(it)?.map { it.toMutable() } },
        queryFilter = queryFilter,
        documentablesToProcessFilter = documentablesToProcessFilter,
    )

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): DocumentablesByPath =
        DocumentablesByPathWithCache(
            unfilteredDocsToProcess = unfilteredDocsToProcess,
            query = query,
            queryFilter = queryFilter,
            documentablesToProcessFilter = documentablesToProcessFilter,
        )

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): DocumentablesByPath =
        DocumentablesByPathWithCache(
            unfilteredDocsToProcess = unfilteredDocsToProcess,
            query = query,
            queryFilter = queryFilter,
            documentablesToProcessFilter = docsToProcessFilter,
        )

    override fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): DocumentablesByPath =
        DocumentablesByPathWithCache(
            unfilteredDocsToProcess = unfilteredDocsToProcess,
            query = query,
            queryFilter = queryFilter,
            documentablesToProcessFilter = docsToProcessFilter,
        )
}

class MutableDocumentablesByPathWithCache(
    private val unfilteredDocsToProcess: Map<String, List<MutableDocumentableWrapper>>,
    private val query: (String) -> List<MutableDocumentableWrapper>?,
    override val queryFilter: DocumentableWrapperFilter = { true },
    override val documentablesToProcessFilter: DocumentableWrapperFilter = { true },
) : MutableDocumentablesByPath {

    override val documentablesToProcess: Map<String, List<MutableDocumentableWrapper>> =
        unfilteredDocsToProcess
            .mapValues { (_, documentables) ->
                documentables.filter(documentablesToProcessFilter)
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
        MutableDocumentablesByPathWithCache(
            unfilteredDocsToProcess = unfilteredDocsToProcess,
            query = query,
            queryFilter = queryFilter,
            documentablesToProcessFilter = documentablesToProcessFilter,
        )

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        MutableDocumentablesByPathWithCache(
            unfilteredDocsToProcess = unfilteredDocsToProcess,
            query = query,
            queryFilter = queryFilter,
            documentablesToProcessFilter = docsToProcessFilter,
        )

    override fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): MutableDocumentablesByPath =
        MutableDocumentablesByPathWithCache(
            unfilteredDocsToProcess = unfilteredDocsToProcess,
            query = query,
            queryFilter = queryFilter,
            documentablesToProcessFilter = docsToProcessFilter,
        )
}
// endregion

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
            documentables.map { it.toMutable() }
        }
    }