package nl.jolanrensen.docProcessor

import com.intellij.openapi.util.TextRange
import mu.KotlinLogging
import nl.jolanrensen.docProcessor.ProcessDocsAction.Parameters
import nl.jolanrensen.docProcessor.gradle.ProcessDocsGradleAction
import nl.jolanrensen.docProcessor.gradle.lifecycle
import org.jetbrains.dokka.*
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.translators.descriptors.DefaultDescriptorToDocumentableTranslator
import org.jetbrains.dokka.base.translators.psi.DefaultPsiToDocumentableTranslator
import org.jetbrains.dokka.model.WithSources
import org.jetbrains.dokka.model.withDescendants
import java.io.File
import java.io.IOException
import java.util.*

private val log = KotlinLogging.logger {}

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
abstract class ProcessDocsAction {

    interface Parameters {
        val baseDir: File
        val sources: DokkaSourceSetImpl
        val sourceRoots: List<File>
        val target: File?
        val processors: List<String>
        val processLimit: Int
    }

    abstract val parameters: Parameters

    protected fun process() {
        // analyse the sources with dokka to get the documentables
        val sourceDocs = analyseSourcesWithDokka()

        log.info { "Found ${sourceDocs.size} source docs: $sourceDocs" }

        // Find all processors
        val processors = findProcessors()

        if (processors.isEmpty())
            log.warn { "No processors found" }
        else
            log.info { "Found processors: ${processors.map { it::class.qualifiedName }}" }

        // Run all processors
        val modifiedDocumentables =
            processors.fold(sourceDocs) { acc, processor ->
                log.lifecycle { "Running processor: ${processor::class.qualifiedName}" }
                processor.processSafely(processLimit = parameters.processLimit, documentablesByPath = acc)
            }

        // filter to only include the modified documentables
        val modifiedDocumentablesPerFile = getModifiedDocumentablesPerFile(modifiedDocumentables)

        log.info {
            "Modified documentables: ${modifiedDocumentablesPerFile.values.flatMap { it.map { it.fullyQualifiedPath } }}"
        }

        // copy the sources to the target folder while replacing all docs in modified documentables
        copyAndModifySources(modifiedDocumentablesPerFile)
    }

    private fun analyseSourcesWithDokka(): Map<String, List<DocumentableWrapper>> {
        // initialize dokka with the sources
        val configuration = DokkaConfigurationImpl(
            sourceSets = listOf(parameters.sources),
        )
        val logger = DokkaBootstrapImpl.DokkaProxyLogger { level, message ->
            with(log) {
                when (level) {
                    "debug" -> debug { message }
                    "info" -> info { message }
                    "progress" -> lifecycle { message }
                    "warn" -> warn { message }
                    "error" -> error { message }
                    else -> info { message }
                }
            }
        }
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
                // TODO: issue #12: support Type Aliases
                // TODO: issue #13: support read-only docs
                val (withSources, withoutSources) = it.partition { it is WithSources }

                // paths without sources are likely generated files or external sources, such as dependencies
                pathsWithoutSources += withoutSources.map { it.dri.fullyQualifiedPath }
                pathsWithoutSources += withoutSources.mapNotNull { it.dri.fullyQualifiedExtensionPath }

                withSources.mapNotNull {
                    val source = (it as WithSources).sources[parameters.sources]
                        ?: return@mapNotNull null

                    DocumentableWrapper.createFromDokkaOrNull(
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
        val availableProcessors: Set<DocProcessor> = ServiceLoader.load(DocProcessor::class.java).toSet()

        val filteredProcessors = parameters
            .processors
            .mapNotNull { name ->
                availableProcessors.find { it::class.qualifiedName == name }
            }.map {
                // create a new instance of the processor, so it can safely be used multiple times
                it::class.java.newInstance()
            }

        return filteredProcessors
    }

    private fun getModifiedDocumentablesPerFile(
        modifiedSourceDocs: Map<String, List<DocumentableWrapper>>,
    ): Map<File, List<DocumentableWrapper>> =
        modifiedSourceDocs
            .entries
            .flatMap {
                it.value.filter {
                    it.isModified // filter out unmodified documentables
                }
            }
            .groupBy { it.file }

    @Throws(IOException::class)
    private fun copyAndModifySources(modifiedDocumentablesPerFile: Map<File, List<DocumentableWrapper>>) {
        for (source in parameters.sourceRoots) {
            for (file in source.walkTopDown()) {
                if (!file.isFile) continue

                val relativePath = parameters.baseDir.toPath().relativize(file.toPath())
                val targetFile = File(parameters.target, relativePath.toString())
                try {
                    targetFile.parentFile.mkdirs()
                } catch (e: Exception) {
                    throw IOException(
                        "Could not create parent directory for $targetFile using target file ${parameters.target}",
                        e,
                    )
                }

                val fileContent = try {
                    file.readText()
                        .replace("\r\n", "\n")
                        .replace("\r", "\n")
                } catch (e: Exception) {
                    throw IOException("Could not read source file $file", e)
                }

                val modifications = modifiedDocumentablesPerFile[file] ?: emptyList()

                val modificationsByRange = modifications
                    .groupBy { it.docFileTextRange.toTextRange() }
                    .mapValues { it.value.first() }
                    .toSortedMap(compareBy { it.startOffset })
                    .map { (docTextRange, documentable) ->
                        getNewDocTextRangeAndDoc(
                            fileContent = fileContent,
                            docTextRange = docTextRange,
                            newDocContent = documentable.docContent,
                            docIndent = documentable.docIndent,
                            sourceHasDocumentation = documentable.sourceHasDocumentation,
                        )
                    }

                val processedFileContent = fileContent.replaceRanges(*modificationsByRange.toTypedArray())

                try {
                    targetFile.writeText(processedFileContent)
                } catch (e: Exception) {
                    throw IOException("Could not write to target file $targetFile", e)
                }
            }
        }
    }

    /**
     * Returns the new range (in [fileContent]) and documentation (with `/** */` if [newDocContent] is not empty)
     * for the documentation found at [docTextRange] in [fileContent],
     * based on the given new [newDocContent].
     */
    private fun getNewDocTextRangeAndDoc(
        fileContent: String,
        docTextRange: TextRange,
        newDocContent: DocContent,
        docIndent: Int,
        sourceHasDocumentation: Boolean,
    ): Pair<IntRange, String> {
        val range = docTextRange.toIntRange()

        return when {
            // don't create empty kdoc, just remove it altogether
            newDocContent.isEmpty() && !sourceHasDocumentation -> range to ""

            // don't create empty kdoc, just remove it altogether
            // We need to expand the replace-range so that the newline is also removed
            newDocContent.isEmpty() && sourceHasDocumentation -> {
                val prependingNewlineIndex = fileContent
                    .indexOfLastOrNullWhile('\n', range.first - 1) { it.isWhitespace() }

                val trailingNewlineIndex = fileContent
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
            newDocContent.isNotEmpty() && !sourceHasDocumentation -> {
                val newKdoc = buildString {
                    append(newDocContent.toDoc())
                    append("\n")
                    append(" ".repeat(docIndent))
                }

                range to newKdoc
            }

            // replace the existing kdoc with the new one
            newDocContent.isNotEmpty() && sourceHasDocumentation -> {
                val newKdoc = newDocContent.toDoc(docIndent).trimStart()

                range to newKdoc
            }

            else -> error("Unreachable")
        }
    }
}