package nl.jolanrensen.docProcessor

import java.util.*

typealias DocumentableWrapperFilter = (DocumentableWrapper) -> Boolean

interface DocumentablesByPath {

    val filter: DocumentableWrapperFilter?

    fun get(path: String): List<DocumentableWrapper>

    operator fun invoke(path: String): List<DocumentableWrapper> = get(path)

    @Suppress("UNCHECKED_CAST")
    fun asMap(): Map<String, List<DocumentableWrapper>>

    /**
     * Returns this as an iterable over all the Documentables. Careful, this is a very expensive operation.
     */
    @Deprecated("Expensive!")
    fun asIterable(): Iterable<Pair<String, List<DocumentableWrapper>>>

    fun toMutable(): MutableDocumentablesByPath

    fun withFilter(filter: DocumentableWrapperFilter): DocumentablesByPath

    companion object {
        val EMPTY: DocumentablesByPath = DocumentablesByPathFromMap(emptyMap())

        fun of(map: Map<String, List<DocumentableWrapper>>): DocumentablesByPath = DocumentablesByPathFromMap(map)
        fun of(map: Map<String, List<MutableDocumentableWrapper>>): MutableDocumentablesByPath =
            MutableDocumentablesByPathFromMap(map)
    }
}

fun Map<String, List<DocumentableWrapper>>.toDocumentablesByPath(): DocumentablesByPath = DocumentablesByPath.of(this)
fun Iterable<Pair<String, List<DocumentableWrapper>>>.toDocumentablesByPath(): DocumentablesByPath =
    toMap().toDocumentablesByPath()

interface MutableDocumentablesByPath : DocumentablesByPath {

    override fun get(path: String): List<MutableDocumentableWrapper>

    override operator fun invoke(path: String): List<MutableDocumentableWrapper> = get(path)

    override fun asMap(): Map<String, List<MutableDocumentableWrapper>>

    override fun asIterable(): Iterable<Pair<String, List<MutableDocumentableWrapper>>>

    override fun withFilter(filter: DocumentableWrapperFilter): MutableDocumentablesByPath
}

// region implementations

internal open class DocumentablesByPathFromMap(
    private val map: Map<String, List<DocumentableWrapper>>,
    override val filter: DocumentableWrapperFilter? = null,
) : DocumentablesByPath, Iterable<Pair<String, List<DocumentableWrapper>>> {

    override fun get(path: String): List<DocumentableWrapper> =
        map[path]?.let { values ->
            filter?.let(values::filter) ?: values
        } ?: emptyList()

    override fun asIterable(): Iterable<Pair<String, List<DocumentableWrapper>>> = this

    override fun iterator(): Iterator<Pair<String, List<DocumentableWrapper>>> =
        map.asSequence()
            .map { (key, values) ->
                key to (filter?.let(values::filter) ?: values)
            }
            .filter { (_, values) -> values.isNotEmpty() }
            .iterator()

    override fun asMap(): Map<String, List<DocumentableWrapper>> = map

    @Suppress("UNCHECKED_CAST")
    override fun toMutable(): MutableDocumentablesByPath = // TODO use filter or not?
        this as? MutableDocumentablesByPath ?: MutableDocumentablesByPathFromMap(
            map.mapValues { (_, documentables) ->
                if (documentables.all { it is MutableDocumentableWrapper }) {
                    documentables as List<MutableDocumentableWrapper>
                } else {
                    documentables.map { it.toMutable() }
                }
            }
        )

    override fun withFilter(filter: DocumentableWrapperFilter): DocumentablesByPath =
        DocumentablesByPathFromMap(map, filter)
}

internal class MutableDocumentablesByPathFromMap(
    private val map: Map<String, List<MutableDocumentableWrapper>>,
    override val filter: DocumentableWrapperFilter? = null,
) : MutableDocumentablesByPath, Iterable<Pair<String, List<MutableDocumentableWrapper>>> {
    override fun get(path: String): List<MutableDocumentableWrapper> =
        map[path]?.let { values ->
            filter?.let(values::filter) ?: values
        } ?: emptyList()

    override fun asIterable(): Iterable<Pair<String, List<MutableDocumentableWrapper>>> = this

    override fun iterator(): Iterator<Pair<String, List<MutableDocumentableWrapper>>> =
        map.asSequence()
            .map { (key, values) ->
                key to (filter?.let(values::filter) ?: values)
            }
            .filter { (_, values) -> values.isNotEmpty() }
            .iterator()

    override fun toMutable(): MutableDocumentablesByPath = this

    override fun asMap(): Map<String, List<MutableDocumentableWrapper>> = map

    override fun withFilter(filter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        MutableDocumentablesByPathFromMap(map, filter)
}

internal open class DocumentablesByPathWithCache(
    val query: (String) -> List<DocumentableWrapper>,
    val queryAll: () -> Map<String, List<DocumentableWrapper>>,
    override val filter: DocumentableWrapperFilter? = null,
) : DocumentablesByPath, Iterable<Pair<String, List<DocumentableWrapper>>> {

    private val cache: MutableMap<String, List<DocumentableWrapper>> = mutableMapOf()

    override fun get(path: String): List<DocumentableWrapper> =
        cache.getOrPut(path) {
            query(path).let { values ->
                filter?.let(values::filter) ?: values
            }
        }

    override fun asIterable(): Iterable<Pair<String, List<DocumentableWrapper>>> = this

    // uses heavy queryAll!
    override fun iterator(): Iterator<Pair<String, List<DocumentableWrapper>>> {
        val all = queryAll()
            .asSequence()
            .map { (key, values) ->
                key to (filter?.let(values::filter) ?: values)
            }
            .filter { (_, values) -> values.isNotEmpty() }
        cache.putAll(all)
        return all.iterator()
    }

    // uses heavy queryAll!
    override fun asMap(): Map<String, List<DocumentableWrapper>> = toMap()

    @Suppress("UNCHECKED_CAST")
    override fun toMutable(): MutableDocumentablesByPath = MutableDocumentablesByPathWithCache(
        query = { query(it).map { it.toMutable() } },
        queryAll = {
            queryAll().mapValues { (_, documentables) ->
                if (documentables.all { it is MutableDocumentableWrapper }) {
                    documentables as List<MutableDocumentableWrapper>
                } else {
                    documentables.map { it.toMutable() }
                }
            }
        }
    )

    override fun withFilter(filter: DocumentableWrapperFilter): DocumentablesByPath =
        DocumentablesByPathWithCache(query, queryAll, filter)
}

internal class MutableDocumentablesByPathWithCache(
    val query: (String) -> List<MutableDocumentableWrapper>,
    val queryAll: () -> Map<String, List<MutableDocumentableWrapper>>,
    override val filter: DocumentableWrapperFilter? = null,
) : MutableDocumentablesByPath, Iterable<Pair<String, List<MutableDocumentableWrapper>>> {

    private val cache: MutableMap<String, List<MutableDocumentableWrapper>> = mutableMapOf()

    override fun get(path: String): List<MutableDocumentableWrapper> =
        cache.getOrPut(path) {
            query(path).let { values ->
                filter?.let(values::filter) ?: values
            }
        }

    override fun asIterable(): Iterable<Pair<String, List<MutableDocumentableWrapper>>> = this

    // uses heavy queryAll!
    override fun iterator(): Iterator<Pair<String, List<MutableDocumentableWrapper>>> {
        val all = queryAll()
            .asSequence()
            .map { (key, values) ->
                key to (filter?.let(values::filter) ?: values)
            }
            .filter { (_, values) -> values.isNotEmpty() }
        cache.putAll(all)
        return all.iterator()
    }

    // uses heavy queryAll!
    @Suppress("UNCHECKED_CAST")
    override fun asMap(): Map<String, List<MutableDocumentableWrapper>> =
        (this as Iterable<Pair<String, List<MutableDocumentableWrapper>>>).toMap()

    override fun toMutable(): MutableDocumentablesByPath = this

    override fun withFilter(filter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        MutableDocumentablesByPathWithCache(query, queryAll, filter)
}
// endregion