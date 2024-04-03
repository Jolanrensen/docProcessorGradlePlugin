package nl.jolanrensen.docProcessor

import org.jgrapht.graph.SimpleDirectedGraph
import java.util.*


open class DocumentablesByPathWithCache<C>(
    val queryNew: (context: C, link: String) -> List<DocumentableWrapper>?,
) : DocumentablesByPath, MutableDocumentablesByPath {

    override var queryFilter: DocumentableWrapperFilter = NO_FILTER

    override var documentablesToProcessFilter: DocumentableWrapperFilter = NO_FILTER

    // graph representing the dependencies between documentables
    private val dependencyGraph = SimpleDirectedGraph<UUID, _>(Edge::class.java as Class<out Edge<UUID>>)

    // holds previous query results
    private val fqNamesCache: MutableMap<UUID, Pair<String, String?>> = mutableMapOf()

    // todo, also track imports, superPaths, now done by UUID, but
    // todo might revert back to keep docContent

    // holds the hashcodes of the source of the documentables, to be updated each time a documentable is queried
    private val docContentSourceHashCodeCache: MutableMap<UUID, Int> = mutableMapOf()

    // holds the resulting docs of the documentables, to be updated each time a documentable has been processed
    // Can be deleted if needsRebuild is true
    private val docContentResultCache: MutableMap<UUID, String> = mutableMapOf()

    private var context: C? = null
    private var docToProcess: MutableDocumentableWrapper? = null
        set(value) {
            field = value

            docsToProcess = docToProcess?.let { docToProcess ->
                listOfNotNull(
                    docToProcess.fullyQualifiedPath,
                    docToProcess.fullyQualifiedExtensionPath,
                ).associateWith { listOf(docToProcess) }
            } ?: emptyMap()
        }

    private var docsToProcess: Map<String, List<MutableDocumentableWrapper>> = emptyMap()

    override val documentablesToProcess: Map<String, List<MutableDocumentableWrapper>>
        get() = when {
            docToProcess == null -> emptyMap()
            documentablesToProcessFilter == NO_FILTER ||
                    documentablesToProcessFilter(docToProcess!!) -> docsToProcess

            else -> emptyMap()
        }

    fun getDocContentResult(docId: UUID): String? = docContentResultCache[docId]

    // we set a context and a documentable to process
    fun updatePreProcessing(context: C, docToProcess: DocumentableWrapper) {
        this.context = context
        this.docToProcess = docToProcess.toMutable()

        fqNamesCache[docToProcess.identifier] = Pair(docToProcess.fullyQualifiedPath, docToProcess.fullyQualifiedExtensionPath)
    }

    override fun query(path: String): List<MutableDocumentableWrapper>? {
        require(context != null) { "updatePreProcessing must be called before query" }
        val queryRes = queryNew(context!!, path)
            ?.map { it.toMutable() }

        // load cached results directly into queries
        // actually, we can't. The final documentable can override get/set args
        // can be done with @include
//        queryRes?.forEach {
//            val needsRebuild = needsRebuild(it)
//            val docContentResult = getDocContentResult(it.identifier)
//            if (!needsRebuild && docContentResult != null) {
//                it.modifyDocContentAndUpdate(docContentResult)
//                println("loading cached ${it.fullyQualifiedPath}/${it.fullyQualifiedExtensionPath}")
//            }
//        }

        return queryRes?.filter(queryFilter)
    }

    fun needsRebuild(): Boolean {
        require(docToProcess != null) { "updatePreProcessing must be called before needsRebuild()" }
        return needsRebuild(docToProcess!!)
    }

    private fun needsRebuild(doc: DocumentableWrapper): Boolean {
        val context = context
        require(context != null) { "updatePreProcessing must be called before needsRebuild" }

        fqNamesCache[doc.identifier] = Pair(doc.fullyQualifiedPath, doc.fullyQualifiedExtensionPath)

        // if source has changed, return true
        val sourceHasChanged = doc.getDocHashcode() != docContentSourceHashCodeCache[doc.identifier]
        val doesNotContainVertex = !dependencyGraph.containsVertex(doc.identifier)
        val doesNotContainResultCache = docContentResultCache[doc.identifier] == null

        if (doesNotContainVertex || doesNotContainResultCache || sourceHasChanged) {

            // update the caches
            docContentSourceHashCodeCache[doc.identifier] = doc.getDocHashcode()
            dependencyGraph.addVertex(doc.identifier)
            docContentResultCache.remove(doc.identifier)
            return true
        }
        val dependencies = dependencyGraph.incomingEdgesOf(doc.identifier).map { it.from }
        if (dependencies.isEmpty()) return false

        // else, check all dependencies for modifications
        val dependencyDocs = dependencies.map {
            val fqNames = fqNamesCache[it]
            if (fqNames == null) {
                null
            } else {
                val (fqName, fqExtName) = fqNames

                // query for a new doc
                buildList {
                    queryNew(context, fqName)?.forEach { add(it) }
                    if (fqExtName != null) {
                        queryNew(context, fqExtName)?.forEach { add(it) }
                    }
                }.firstOrNull {
                    it.identifier != doc.identifier
                }
            }
        }

        val needsRebuild = dependencyDocs
            .map { it == null || needsRebuild(it) }
            .any { it } // mapping first to increase the documentableWrapperCache

        return needsRebuild
    }

    fun updatePostProcessing(resultDoc: DocumentableWrapper) {
        // update dependency graph
        val oldIncomingEdges = dependencyGraph.incomingEdgesOf(resultDoc.identifier)
        val newIncomingEdges = resultDoc.dependsOn.map {
            Edge(from = it.identifier, to = resultDoc.identifier)
        }.toSet()

        for (newEdge in (newIncomingEdges - oldIncomingEdges)) {
            dependencyGraph.addVertex(newEdge.from)
            dependencyGraph.addVertex(newEdge.to)
            dependencyGraph.addEdge(newEdge.from, newEdge.to, newEdge)
        }
        for (removedEdge in (oldIncomingEdges - newIncomingEdges)) {
            dependencyGraph.removeEdge(removedEdge)
        }

        // and caches
        docContentResultCache[resultDoc.identifier] = resultDoc.docContent
        fqNamesCache[resultDoc.identifier] = Pair(resultDoc.fullyQualifiedPath, resultDoc.fullyQualifiedExtensionPath)
        resultDoc.dependsOn.forEach {
            updatePostProcessing(it)
        }
    }

    override fun toMutable(): MutableDocumentablesByPath = this

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        apply { this.queryFilter = queryFilter }

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        apply { this.documentablesToProcessFilter = docsToProcessFilter}

    override fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): MutableDocumentablesByPath =
        apply {
            this.queryFilter = queryFilter
            this.documentablesToProcessFilter = docsToProcessFilter
        }
}