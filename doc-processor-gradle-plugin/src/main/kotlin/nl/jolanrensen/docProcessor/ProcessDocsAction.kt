package nl.jolanrensen.docProcessor

import nl.jolanrensen.docProcessor.ProcessDocsAction.Parameters
import nl.jolanrensen.docProcessor.gradle.ProcessDocsGradleAction
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

/**
 * Process docs action.
 *
 * When [process] is executed, it will:
 *  - analyse the sources with dokka to get the documentables ([analyseSourcesWithDokka])
 *  - Use a [ServiceLoader] to get all available [DocProcessors][DocProcessor] ([findProcessors])
 *  - Filter found [DocProcessors][DocProcessor] by [Parameters.processors] ([findProcessors])
 *  - Run filtered [DocProcessors][DocProcessor] on the documentables in order of [Parameters.processors] ([process])
 *  - Collect all modified documentables per file ([getModifiedDocumentablesPerFile])
 *  - Write files to the [Parameters.target] directory containing modified text ([copyAndModifySources])
 *
 * @see [ProcessDocsGradleAction] for a Gradle specific implementation.
 */
abstract class ProcessDocsAction : SimpleLogger {

    interface Parameters {
        val baseDir: File
        val sources: DokkaSourceSetImpl
        val sourceRoots: List<File>
        val target: File?
        val debug: Boolean
        val processors: List<String>
        val processLimit: Int

        companion object {
            operator fun invoke(
                baseDir: File,
                sources: DokkaSourceSetImpl,
                sourceRoots: List<File>,
                target: File?,
                debug: Boolean,
                processors: List<String>,
                processLimit: Int,
            ): Parameters = object : Parameters {
                override val baseDir: File = baseDir
                override val sources: DokkaSourceSetImpl = sources
                override val sourceRoots: List<File> = sourceRoots
                override val target: File? = target
                override val debug: Boolean = debug
                override val processors: List<String> = processors
                override val processLimit: Int = processLimit
            }
        }
    }

    companion object {
        operator fun invoke(
            baseDir: File,
            sources: DokkaSourceSetImpl,
            sourceRoots: List<File>,
            target: File?,
            debug: Boolean,
            processors: List<String>,
            processLimit: Int,
        ): ProcessDocsAction = object : ProcessDocsAction() {
            override val parameters = Parameters(
                baseDir = baseDir,
                sources = sources,
                sourceRoots = sourceRoots,
                target = target,
                debug = debug,
                processors = processors,
                processLimit = processLimit,
            )
        }
    }

    abstract val parameters: Parameters

    override val logEnabled: Boolean
        get() = parameters.debug

    protected fun process() {
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

        println("Modified documentables: ${modifiedDocumentablesPerFile.values.flatMap { it.map { it.fullyQualifiedPath } }}")

        // copy the sources to the target folder while replacing all docs in modified documentables
        copyAndModifySources(modifiedDocumentablesPerFile)
    }

    private fun analyseSourcesWithDokka(): Map<String, List<DocumentableWrapper>> {
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

                // paths without sources are likely generated files or external sources, such as dependencies
                pathsWithoutSources += withoutSources.map { it.dri.fullyQualifiedPath }
                pathsWithoutSources += withoutSources.mapNotNull { it.dri.fullyQualifiedExtensionPath }

                withSources.mapNotNull {
                    val source = (it as WithSources).sources[parameters.sources]!!

                    DocumentableWrapper.createOrNull(
                        documentable = it,
                        source = source,
                        logger = logger,
                    )
                }
            }
        }

        // collect the documentables with sources per path
        val documentablesPerPath: MutableMap<String, List<DocumentableWrapper>> = documentables
            .flatMap { doc ->
                listOfNotNull(doc.fullyQualifiedPath, doc.fullyQualifiedExtensionPath).map { it to doc }
            }
            .groupBy { it.first }
            .mapValues { it.value.map { it.second } }
            .toMutableMap()

        // add the paths for documentables without sources to the map
        for (path in pathsWithoutSources) {
            if (path !in documentablesPerPath) {
                documentablesPerPath[path] = emptyList()
            }
        }

        return documentablesPerPath
    }

    private fun findProcessors(): List<DocProcessor> {
        val availableProcessors: Set<DocProcessor> =
            ServiceLoader.load(DocProcessor::class.java).toSet()

        val filteredProcessors = parameters.processors
            .mapNotNull { name ->
                availableProcessors.find { it::class.qualifiedName == name }
            }

        // set loggers enabled
        filteredProcessors.forEach { it.logEnabled = parameters.debug }

        return filteredProcessors
    }

    private fun getModifiedDocumentablesPerFile(
        modifiedSourceDocs: Map<String, List<DocumentableWrapper>>,
    ): Map<File, List<DocumentableWrapper>> =
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

    private fun copyAndModifySources(modifiedDocumentablesPerFile: Map<File, List<DocumentableWrapper>>) {
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
                    .mapValues { it.value.first() }
                    .toSortedMap(compareBy { it.startOffset })
                    .map { (textRange, documentable) ->
                        val range = textRange!!.toIntRange()

                        val docContent = documentable.docContent
                        val indent = documentable.docIndent
                        val sourceHasDocumentation = documentable.sourceHasDocumentation

                        when {
                            // don't create empty kdoc, just remove it altogether
                            docContent.isEmpty() && !sourceHasDocumentation -> range to ""

                            // don't create empty kdoc, just remove it altogether
                            // We need to expand the replace-range so that the newline is also removed
                            docContent.isEmpty() && sourceHasDocumentation -> {
                                val prependingNewlineIndex = content
                                    .indexOfLastOrNullWhile('\n', range.first - 1) { it.isWhitespace() }

                                val trailingNewlineIndex = content
                                    .indexOfFirstOrNullWhile('\n', range.last + 1) { it.isWhitespace() }

                                val newRange = when {
                                    prependingNewlineIndex != null && trailingNewlineIndex != null ->
                                        prependingNewlineIndex..range.last

                                    trailingNewlineIndex != null ->
                                        range.first..trailingNewlineIndex

                                    else -> range
                                }

                                newRange to ""
                            }

                            // create a new kdoc at given range
                            docContent.isNotEmpty() && !sourceHasDocumentation -> {
                                val newKdoc = buildString {
                                    append(docContent.toDoc())
                                    append("\n")
                                    append(" ".repeat(indent!!))
                                }

                                range to newKdoc
                            }

                            // replace the existing kdoc with the new one
                            docContent.isNotEmpty() && sourceHasDocumentation -> {
                                val newKdoc = docContent.toDoc(indent!!).trimStart()

                                range to newKdoc
                            }

                            else -> error("Unreachable")
                        }
                    }

                val processedContent = content.replaceRanges(*modificationsByRange.toTypedArray())

                targetFile.writeText(processedContent)
            }
        }
    }
}