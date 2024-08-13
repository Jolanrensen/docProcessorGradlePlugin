package nl.jolanrensen.docProcessor

import java.util.UUID

typealias DocumentableWrapperFilter = (DocumentableWrapper) -> Boolean

@Suppress("ClassName")
internal data object NO_FILTER : DocumentableWrapperFilter {
    override fun invoke(p1: DocumentableWrapper): Boolean = true
}

interface DocumentablesByPath {

    val queryFilter: DocumentableWrapperFilter

    val documentablesToProcessFilter: DocumentableWrapperFilter

    val documentablesToProcess: Map<String, List<DocumentableWrapper>>

    /**
     * Whether [DocumentableWrapper.getAllFullPathsFromHereForTargetPath] needs to be used to do
     * [queries][query].
     * Usually `false` for IntelliJ plugin, since it can do relative querying,
     * but `true` for the Gradle plugin.
     */
    val needToQueryAllPaths: Boolean

    /**
     * Returns a list of [DocumentableWrapper]s for the given [path].
     *
     * Returns empty list if [path] exists in the project
     * but no [DocumentableWrapper] is found.
     *
     * Returns `null` if no [DocumentableWrapper] is found for the given [path] and [path]
     * does not exist in the project.
     */
    fun query(path: String, queryContext: DocumentableWrapper, canBeCache: Boolean = false): List<DocumentableWrapper>?

    @Deprecated(
        "Use query instead",
        ReplaceWith("query(path, queryContext, canBeCache)"),
        level = DeprecationLevel.ERROR,
    )
    operator fun get(
        path: String,
        queryContext: DocumentableWrapper,
        canBeCache: Boolean = false,
    ): List<DocumentableWrapper>? = query(path, queryContext, canBeCache)

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

interface MutableDocumentablesByPath : DocumentablesByPath {

    override fun query(
        path: String,
        queryContext: DocumentableWrapper,
        canBeCache: Boolean,
    ): List<MutableDocumentableWrapper>?

    override operator fun get(
        path: String,
        queryContext: DocumentableWrapper,
        canBeCache: Boolean,
    ): List<MutableDocumentableWrapper>? = query(path, queryContext, canBeCache)

    override operator fun get(identifier: UUID): MutableDocumentableWrapper? =
        documentablesToProcess.values
            .firstNotNullOfOrNull { it.firstOrNull { it.identifier == identifier } }

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): MutableDocumentablesByPath

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): MutableDocumentablesByPath

    override fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): MutableDocumentablesByPath

    override val documentablesToProcess: Map<String, List<MutableDocumentableWrapper>>

    override fun toMutable(): MutableDocumentablesByPath = this
}

@Suppress("UNCHECKED_CAST")
fun <T : DocumentablesByPath> T.withoutFilters(): T =
    when {
        queryFilter == NO_FILTER && documentablesToProcessFilter == NO_FILTER -> this
        else -> this.withFilters(NO_FILTER, NO_FILTER) as T
    }

fun Map<String, List<DocumentableWrapper>>.toDocumentablesByPath(): DocumentablesByPath = DocumentablesByPath.of(this)

fun Iterable<Pair<String, List<DocumentableWrapper>>>.toDocumentablesByPath(): DocumentablesByPath =
    toMap().toDocumentablesByPath()

/**
 * Converts a [Map]<[String], [List]<[DocumentableWrapper]>> to
 * [Map]<[String], [List]<[MutableDocumentableWrapper]>>.
 *
 * The [MutableDocumentableWrapper] is a copy of the original [DocumentableWrapper].
 */
@Suppress("UNCHECKED_CAST")
internal fun Map<String, List<DocumentableWrapper>>.toMutable(): Map<String, List<MutableDocumentableWrapper>> =
    mapValues { (_, documentables) ->
        if (documentables.all { it is MutableDocumentableWrapper }) {
            documentables as List<MutableDocumentableWrapper>
        } else {
            documentables.map { it.toMutable() }
        }
    }

fun <K, V> MutableMap<K, MutableList<V>>.add(key: K, value: V) = getOrPut(key) { mutableListOf() }.add(value)
