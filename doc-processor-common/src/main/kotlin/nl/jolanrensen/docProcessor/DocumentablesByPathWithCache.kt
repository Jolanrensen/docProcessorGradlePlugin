package nl.jolanrensen.docProcessor

import nl.jolanrensen.docProcessor.defaultProcessors.IncludeDocAnalyzer
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.traverse.BreadthFirstIterator
import org.jgrapht.traverse.TopologicalOrderIterator
import java.util.UUID

open class DocumentablesByPathWithCache(
    val processLimit: Int,
    val queryNew: (context: DocumentableWrapper, link: String) -> List<DocumentableWrapper>?,
    val logDebug: (message: () -> String) -> Unit,
) : DocumentablesByPath,
    MutableDocumentablesByPath {

    override var queryFilter: DocumentableWrapperFilter = NO_FILTER

    override var documentablesToProcessFilter: DocumentableWrapperFilter = NO_FILTER

    // graph representing the dependencies between documentables
    @Suppress("UNCHECKED_CAST")
    private var dependencyGraph = SimpleDirectedGraph<UUID, Edge<UUID>>(Edge::class.java as Class<out Edge<UUID>>)

    // holds the hashcodes of the source of the documentables, to be updated each time a documentable is queried
    private val docContentSourceHashCodeCache: MutableMap<UUID, Int> = mutableMapOf()

    // holds the resulting docs of the documentables, to be updated each time a documentable has been processed
    // Can be deleted if needsRebuild is true
    // cannot be used in query(), see postIncludeDocContentCache instead
    private val docContentResultCache: MutableMap<UUID, DocContent> = mutableMapOf()

    // holds the intermediate (post-@include) docContent states of the docs
    // this is used on query
    private val postIncludeDocContentCache: MutableMap<UUID, DocContent> = mutableMapOf()

    private var docToProcess: MutableDocumentableWrapper? = null
    private var docsToProcess: MutableMap<String, MutableList<MutableDocumentableWrapper>> = mutableMapOf()
    private var queryCache: MutableMap<Pair<UUID, String>, MutableList<MutableDocumentableWrapper>> = mutableMapOf()

    override val documentablesToProcess: Map<String, List<MutableDocumentableWrapper>>
        get() = when {
            docToProcess == null -> emptyMap()

            documentablesToProcessFilter == NO_FILTER -> docsToProcess

            else -> docsToProcess.mapValues { (_, documentables) ->
                documentables.filter(documentablesToProcessFilter)
            }
        }

    fun getDocContentResult(docId: UUID): DocContent? = docContentResultCache[docId]

    /**
     * called from [nl.jolanrensen.docProcessor.services.PostIncludeDocProcessorCacheCollector]
     */
    fun updatePostIncludeDocContentResult(documentable: DocumentableWrapper) {
        postIncludeDocContentCache[documentable.identifier] = documentable.docContent
    }

    /**
     * we set a context and a documentable to process
     * returns whether it needs a rebuild
     */
    fun updatePreProcessing(docToProcess: DocumentableWrapper): Boolean {
        val doc = docToProcess.toMutable()
        this.docToProcess = doc
        this.docsToProcess = doc.paths
            .associateWith { mutableListOf(doc) }
            .toMutableMap()

        this.queryCache = mutableMapOf()

        // build local dependency graph from docToProcess and update the global dependencyGraph
        val graph = IncludeDocAnalyzer.getAnalyzedResult(
            processLimit = processLimit - 1, // catch circular dependencies
            documentablesByPath = this,
            analyzeQueriesToo = true,
        )
        for (vertex in graph.vertexSet()) {
            if (!dependencyGraph.containsVertex(vertex.identifier)) {
                dependencyGraph.addVertex(vertex.identifier)
            }

            val oldIncomingEdges = dependencyGraph.incomingEdgesOf(vertex.identifier)
            val newIncomingEdges = graph
                .incomingEdgesOf(vertex)
                .map {
                    Edge(it.from.identifier, it.to.identifier)
                }.toSet()

            for (newEdge in (newIncomingEdges - oldIncomingEdges)) {
                dependencyGraph.addVertex(newEdge.from)
                dependencyGraph.addVertex(newEdge.to)
                dependencyGraph.addEdge(newEdge.from, newEdge.to, newEdge)
            }
            for (removedEdge in (oldIncomingEdges - newIncomingEdges)) {
                dependencyGraph.removeEdge(removedEdge)
            }
        }

        // graph may not contain doc if it has no dependencies, so add it to the list
        val orderedList = TopologicalOrderIterator(graph)
            .asSequence()
            .toList()
            .let { if (doc !in it) it + doc else it }

        // rebuild docsToProcess, this time ordered and with dependent docs for PostIncludeDocProcessor that are not
        // up to date. Also update query cache
        for (dependencyDoc in orderedList) {
            // put doc into query cache
            dependencyDoc.paths.forEach {
                queryCache.add(
                    key = Pair(dependencyDoc.identifier, it),
                    value = dependencyDoc.toMutable(),
                )
            }
        }

        var needsRebuild = false
        for (dependencyDoc in orderedList) {
            val mutable = dependencyDoc.toMutable()

            // put doc into process queue if it needs a rebuild
            if (needsRebuild(dependencyDoc)) {
                needsRebuild = true
                dependencyDoc.paths
                    .forEach {
                        docsToProcess.add(it, mutable)
                    }
            }
        }

        return needsRebuild
    }

    /** retrieve a doc by identifier from queryCache or docsToProcess */
    override fun get(identifier: UUID): MutableDocumentableWrapper? =
        queryCache.values
            .firstNotNullOfOrNull { it.firstOrNull { it.identifier == identifier } }
            ?: super<MutableDocumentablesByPath>.get(identifier)

    override val needToQueryAllPaths: Boolean = false

    override fun query(
        path: String,
        queryContext: DocumentableWrapper,
        canBeCache: Boolean,
    ): List<MutableDocumentableWrapper>? {
        val res = queryCache.getOrPut(Pair(queryContext.identifier, path)) {
            queryNew(queryContext, path)
                ?.filter(queryFilter)
                ?.map { it.toMutable() }
                ?.toMutableList()
                ?: return@query null
        }

        // load cached results directly into queries
        if (canBeCache) {
            for (doc in res) {
                // try to receive a post-include cached version
                val docContentResult = postIncludeDocContentCache[doc.identifier]
                if (docContentResult != null) {
                    doc.modifyDocContentAndUpdate(docContentResult)
                    logDebug {
                        "loading post-include cached ${
                            doc.fullyQualifiedPath
                        }/${doc.fullyQualifiedExtensionPath}: $docContentResult"
                    }
                }
            }
        }

        return res
    }

    private fun needsRebuild(doc: DocumentableWrapper): Boolean {
        // if source has changed, return true
        val sourceHasChanged = doc.getDocHashcode() != docContentSourceHashCodeCache[doc.identifier]
        val doesNotContainResultCache = docContentResultCache[doc.identifier] == null
        val doesNotContainVertex = !dependencyGraph.containsVertex(doc.identifier)

        if (doesNotContainVertex) dependencyGraph.addVertex(doc.identifier)

        val dependencies = dependencyGraph.incomingEdgesOf(doc.identifier).map { it.from }

        // else, check all dependencies for modifications
        val dependencyDocs = dependencies.map { get(it) }

        val dependenciesNeedsRebuild = dependencyDocs
            .map { it == null || needsRebuild(it) } // mapping first to increase the documentableWrapperCache
            .any { it }

        val needsRebuild = sourceHasChanged ||
            doesNotContainVertex ||
            doesNotContainResultCache ||
            dependenciesNeedsRebuild

        if (needsRebuild) {
            // update the caches
            docContentSourceHashCodeCache[doc.identifier] = doc.getDocHashcode()

            docContentResultCache.remove(doc.identifier)
            postIncludeDocContentCache.remove(doc.identifier)

            // and remove caches of docs dependent on this doc
            BreadthFirstIterator(dependencyGraph, doc.identifier).forEach {
                docContentResultCache.remove(it)
                postIncludeDocContentCache.remove(it)
            }
        }

        return needsRebuild
    }

    fun updatePostProcessing() {
        for (doc in docsToProcess.values.flatten()) {
            docContentResultCache[doc.identifier] = doc.docContent
        }
    }

    override fun toMutable(): MutableDocumentablesByPath = this

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        apply { this.queryFilter = queryFilter }

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        apply { this.documentablesToProcessFilter = docsToProcessFilter }

    override fun withFilters(
        queryFilter: DocumentableWrapperFilter,
        docsToProcessFilter: DocumentableWrapperFilter,
    ): MutableDocumentablesByPath =
        apply {
            this.queryFilter = queryFilter
            this.documentablesToProcessFilter = docsToProcessFilter
        }
}
