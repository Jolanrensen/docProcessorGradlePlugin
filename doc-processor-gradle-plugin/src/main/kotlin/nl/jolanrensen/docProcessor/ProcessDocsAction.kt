package nl.jolanrensen.docProcessor

import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaGenerator
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.translators.descriptors.DefaultDescriptorToDocumentableTranslator
import org.jetbrains.dokka.base.translators.psi.DefaultPsiToDocumentableTranslator
import org.jetbrains.dokka.model.WithSources
import org.jetbrains.dokka.model.withDescendants
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import java.io.File
import java.util.*


abstract class ProcessDocsAction : WorkAction<ProcessDocsAction.MutableParameters> {

    interface Parameters : WorkParameters {
        val baseDir: File
        val sources: DokkaSourceSetImpl
        val sourceRoots: List<File>
        val target: File?
        val debug: Boolean
        val processors: List<String>
        val processLimit: Int
    }

    interface MutableParameters : Parameters {
        override var baseDir: File
        override var sources: DokkaSourceSetImpl
        override var sourceRoots: List<File>
        override var target: File?
        override var debug: Boolean
        override var processors: List<String>
        override var processLimit: Int
    }

    private fun println(message: String) {
        if (parameters.debug) kotlin.io.println(message)
    }

    override fun execute() {
        try {
            process()
        } catch (e: Throwable) {
            if (parameters.debug) e.printStackTrace()
            throw e
        }
    }

    private fun process() {
        // analyse the sources with dokka to get the documentables
        val sourceDocs = analyseSourcesWithDokka()

        println("Found ${sourceDocs.size} source docs: $sourceDocs")

        // Find all processors
        val processors = findProcessors()

        if (processors.isEmpty())
            println("No processors found")
        else
            println("Found processors: ${processors.map { it::class.qualifiedName }}")

        // Run all processors
        val modifiedDocumentables =
            processors.fold(sourceDocs) { acc, processor ->
                println("Running processor: ${processor::class.qualifiedName}")
                processor.process(parameters, acc)
            }

        // filter to only include the modified documentables
        val modifiedDocumentablesPerFile = getModifiedDocumentablesPerFile(modifiedDocumentables)

        println("Modified documentables: ${modifiedDocumentablesPerFile.values.flatMap { it.map { it.path } }}")

        // copy the sources to the target folder while replacing all docs in modified documentables
        copyAndModifySources(modifiedDocumentablesPerFile)
    }

    private fun findProcessors(): List<DocProcessor> {
        val availableProcessors: Set<DocProcessor> =
            ServiceLoader.load(DocProcessor::class.java).toSet()

        val filteredProcessors = parameters.processors
            .mapNotNull { name ->
                availableProcessors.find { it::class.qualifiedName == name }
            }
        return filteredProcessors
    }

    private fun analyseSourcesWithDokka(): Map<String, List<DocumentableWithSource>> {
        // initialize dokka with the sources
        val configuration = DokkaConfigurationImpl(
            sourceSets = listOf(parameters.sources),
        )
        val logger = DokkaConsoleLogger()

        val dokkaGenerator = DokkaGenerator(configuration, logger)

        // get the sourceToDocumentableTranslators from DokkaBase, both for java and kotlin files
        val context = dokkaGenerator.initializePlugins(configuration, logger, listOf(DokkaBase()))
        val translators = context[CoreExtensions.sourceToDocumentableTranslator]
            .filter {
                it is DefaultPsiToDocumentableTranslator || // java
                        it is DefaultDescriptorToDocumentableTranslator // kotlin
            }

        require(translators.any { it is DefaultPsiToDocumentableTranslator }) {
            "Could not find DefaultPsiToDocumentableTranslator"
        }

        require(translators.any { it is DefaultDescriptorToDocumentableTranslator }) {
            "Could not find DefaultDescriptorToDocumentableTranslator"
        }

        // execute the translators on the sources to gather the modules
        val modules = translators.map {
            it.invoke(
                sourceSet = parameters.sources,
                context = context,
            )
        }

        // collect the right documentables from the modules (only linkable elements with docs)
        val pathsWithoutSources = mutableSetOf<String>()
        val documentables = modules.flatMap {
            it.withDescendants().let {
                val (withSources, withoutSources) = it.partition { it is WithSources }

                pathsWithoutSources += withoutSources.map { it.dri.path }
                pathsWithoutSources += withoutSources.mapNotNull { it.dri.extensionPath }

                withSources.mapNotNull {
                    val source = (it as WithSources).sources[parameters.sources]!!

                    DocumentableWithSource.createOrNull(
                        documentable = it,
                        source = source,
                        logger = logger,
                    )
                }
            }
        }

        val documentablesPerPath: MutableMap<String, List<DocumentableWithSource>> = documentables
            .flatMap { doc ->
                buildList {
                    this += doc.path to doc

                    doc.extensionPath?.let {
                        this += it to doc
                    }
                }
            }
            .groupBy { it.first }
            .mapValues { it.value.map { it.second } }
            .toMutableMap()

        for (path in pathsWithoutSources) {
            if (path !in documentablesPerPath) {
                documentablesPerPath[path] = emptyList()
            }
        }

        return documentablesPerPath
    }

    private fun getModifiedDocumentablesPerFile(
        modifiedSourceDocs: Map<String, List<DocumentableWithSource>>,
    ): Map<File, List<DocumentableWithSource>> =
        modifiedSourceDocs
            .entries
            .flatMap {
                it.value.filter {
                    it.isModified && // filter out unmodified documentables and those without a place to put the docs
                            it.docTextRange != null &&
                            it.docIndent != null
                }
            }
            .groupBy { it.file }


    private fun copyAndModifySources(modifiedDocumentablesPerFile: Map<File, List<DocumentableWithSource>>) {
        for (source in parameters.sourceRoots) {
            for (file in source.walkTopDown()) {
                if (!file.isFile) continue

                val relativePath = parameters.baseDir.toPath().relativize(file.toPath())
                val targetFile = File(parameters.target, relativePath.toString())
                targetFile.parentFile.mkdirs()

                val content = file.readText()
                val modifications = modifiedDocumentablesPerFile[file] ?: emptyList()

                val modificationsByRange = modifications
                    .groupBy { it.docTextRange!! }
                    .mapValues { it.value.first().let { Pair(it.docContent, it.docIndent) } }
                    .toSortedMap(compareBy { it.startOffset })
                    .map { (textRange, kdocAndIndent) ->
                        val range = textRange!!.startOffset until textRange.endOffset
                        val (kdoc, indent) = kdocAndIndent

                        if (kdoc.isBlank())
                            return@map range to "" // don't create empty kdoc, just remove it altogether

                        var fixedKdoc = kdoc.trim()

                        // fix start and end newlines of kdoc
                        if (fixedKdoc.split('\n').size > 1) {
                            if (fixedKdoc.first() != '\n') fixedKdoc = "\n$fixedKdoc"
                            if (fixedKdoc.last() != '\n') fixedKdoc = "$fixedKdoc\n"
                        }

                        val newKdoc = fixedKdoc.toDoc(indent!!).trimStart()


                        range to newKdoc
                    }.toMap()

                val processedContent = content.replaceRanges(modificationsByRange)

                targetFile.writeText(processedContent)
            }
        }
    }


}




