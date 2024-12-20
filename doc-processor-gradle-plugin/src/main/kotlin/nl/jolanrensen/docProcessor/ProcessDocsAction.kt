package nl.jolanrensen.docProcessor

import com.intellij.openapi.util.TextRange
import io.github.oshai.kotlinlogging.KotlinLogging
import nl.jolanrensen.docProcessor.gradle.ProcessDocsGradleAction
import nl.jolanrensen.docProcessor.gradle.lifecycle
import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.DokkaBootstrapImpl
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaGenerator
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.model.WithSources
import org.jetbrains.dokka.model.withDescendants
import java.io.File
import java.io.IOException
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

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
        val exportAsHtmlDir: File?
        val processors: List<String>
        val processLimit: Int
        val arguments: Map<String, Any?>
        val outputReadOnly: Boolean
        val htmlOutputReadOnly: Boolean
    }

    abstract val parameters: Parameters

    protected fun process() {
        // analyse the sources with dokka to get the documentables
        log.lifecycle { "Analyzing sources..." }
        val (sourceDocs, time) = measureTimedValue {
            analyseSourcesWithDokka()
        }
        log.lifecycle { "  - Finished in ${time.toString(DurationUnit.SECONDS)}." }

        // Find all processors
        val processors = findProcessors(parameters.processors, parameters.arguments)

        if (processors.isEmpty()) {
            log.warn { "No processors found" }
        } else {
            log.info { "Found processors: ${processors.map { it::class.qualifiedName }}" }
        }

        // Run all processors
        val modifiedDocumentables =
            processors
                .fold(sourceDocs) { acc, processor ->
                    log.lifecycle { "Running processor: ${processor::class.qualifiedName}..." }
                    val (docs, time) = measureTimedValue {
                        processor.processSafely(processLimit = parameters.processLimit, documentablesByPath = acc)
                    }
                    log.lifecycle { "  - Finished in ${time.toString(DurationUnit.SECONDS)}." }
                    docs
                }.documentablesToProcess

        // filter to only include the modified documentables
        val modifiedDocumentablesPerFile = getModifiedDocumentablesPerFile(modifiedDocumentables)

        val documentablesToExcludeFromSourcesPerFile = getDocumentablesToExcludePerFile(modifiedDocumentables)

        log.info {
            "Modified documentables: ${
                modifiedDocumentablesPerFile.values.flatMap {
                    it.map { it.fullyQualifiedPath }
                }
            }"
        }

        // copy the sources to the target folder while replacing all docs in modified documentables
        copyAndModifySources(modifiedDocumentablesPerFile, documentablesToExcludeFromSourcesPerFile)

        // export htmls
        exportHtmls(modifiedDocumentables.values.flatten())
    }

    private fun analyseSourcesWithDokka(): DocumentablesByPath {
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
                    "warn" -> if (!message.startsWith("Couldn't resolve link for")) warn { message }
                    "error" -> error { message }
                    else -> info { message }
                }
            }
        }
        val dokkaGenerator = DokkaGenerator(configuration, logger)

        // get the sourceToDocumentableTranslators from DokkaBase, both for java and kotlin files
        val context = dokkaGenerator.initializePlugins(configuration, logger, listOf(DokkaBase()))
        val translators = context[CoreExtensions.sourceToDocumentableTranslator]

        require(translators.isNotEmpty()) {
            "Could not find any translators"
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
        val documentables = mutableListOf<DocumentableWrapper>()
        modules.flatMap { it.withDescendants() }.let { rawDocs ->
            // TODO: issue #13: support read-only docs
            val (withSources, withoutSources) = rawDocs.partition { it is WithSources }

            // paths without sources are likely generated files or external sources, such as dependencies
            pathsWithoutSources += withoutSources.map { it.dri.fullyQualifiedPath }
            pathsWithoutSources += withoutSources.mapNotNull { it.dri.fullyQualifiedExtensionPath }

            documentables += withSources.mapNotNull {
                val source = (it as WithSources).sources[parameters.sources]
                    ?: return@mapNotNull null

                DocumentableWrapper.createFromDokkaOrNull(
                    documentable = it,
                    source = source,
                    logger = logger,
                )
            }
        }

        // collect the documentables with sources per path
        val documentablesPerPath: MutableMap<String, List<DocumentableWrapper>> = documentables
            .flatMap { doc -> doc.paths.map { it to doc } }
            .groupBy { it.first }
            .mapValues { it.value.map { it.second } }
            .toMutableMap()

        // add the paths for documentables without sources to the map
        for (path in pathsWithoutSources) {
            if (path !in documentablesPerPath) {
                documentablesPerPath[path] = emptyList()
            }
        }

        log.info { "Found ${documentablesPerPath.size} source docs: $documentablesPerPath" }

        return DocumentablesByPath.of(documentablesPerPath)
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
            }.groupBy { it.file }

    private fun getDocumentablesToExcludePerFile(
        modifiedSourceDocs: Map<String, List<DocumentableWrapper>>,
    ): Map<File, List<DocumentableWrapper>> =
        modifiedSourceDocs
            .entries
            .flatMap {
                it.value.filter {
                    it.annotations.any {
                        it.simpleName == ExcludeFromSources::class.simpleName
                    }
                }
            }.groupBy { it.file }

    /**
     * Enables `@file:ExcludeFromSources` annotation to work.
     */
    private fun File.containsExcludeFromSources(): Boolean {
        for (line in bufferedReader().lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("package") -> return false
                trimmed.startsWith("import") -> return false
                trimmed.contains("@file:${ExcludeFromSources::class.simpleName}") -> return true
                trimmed.matches("@file:\\[.*${ExcludeFromSources::class.simpleName}.*]".toRegex()) -> return true
            }
        }
        return false
    }

    @Throws(IOException::class)
    private fun copyAndModifySources(
        modifiedDocumentablesPerFile: Map<File, List<DocumentableWrapper>>,
        documentablesToExcludeFromPerFile: Map<File, List<DocumentableWrapper>>,
    ) {
        for (source in parameters.sourceRoots) {
            for (file in source.walkTopDown()) {
                if (!file.isFile) continue
                if (file.containsExcludeFromSources()) continue

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
                    file
                        .readText()
                        .replace("\r\n", "\n")
                        .replace("\r", "\n")
                } catch (e: Exception) {
                    throw IOException("Could not read source file $file", e)
                }

                val documentablesToExclude = documentablesToExcludeFromPerFile[file] ?: emptyList()
                val idsToExclude = documentablesToExclude.map { it.identifier }
                val modifications = modifiedDocumentablesPerFile[file] ?: emptyList()

                val docModificationsByRange = modifications
                    .filter { it.identifier !in idsToExclude } // skip modification if entire documentable is excluded
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

                val exclusionModificationsByRange = documentablesToExclude
                    .groupBy { it.fileTextRange.toTextRange() }
                    .mapValues { it.value.first() }
                    .toSortedMap(compareBy { it.startOffset })
                    .map { (fileTextRange, _) ->
                        fileTextRange.toIntRange() to ""
                    }

                val processedFileContent = fileContent.replaceNonOverlappingRanges(
                    *(docModificationsByRange + exclusionModificationsByRange).toTypedArray(),
                )

                try {
                    targetFile.apply {
                        setWritable(true, false)
                        delete()
                        writeText(processedFileContent)
                        if (parameters.outputReadOnly) {
                            setReadOnly()
                        }
                    }
                } catch (e: Exception) {
                    throw IOException("Could not write to target file $targetFile", e)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun exportHtmls(documentables: List<DocumentableWrapper>) {
        val htmlDir = parameters.exportAsHtmlDir?.also { it.mkdirs() }
            ?: throw IOException("No exportAsHtmlDir specified")

        for (doc in documentables) {
            val exportHtmlAnnotation = doc.annotations.find {
                it.simpleName == ExportAsHtml::class.simpleName
            }
            if (exportHtmlAnnotation == null) continue

            val addTheme = exportHtmlAnnotation.arguments
                .firstOrNull { (it, _) -> it == ExportAsHtml::theme.name }
                ?.second as? Boolean?
                ?: true

            val stripReferences = exportHtmlAnnotation.arguments
                .firstOrNull { (it, _) -> it == ExportAsHtml::stripReferences.name }
                ?.second as? Boolean?
                ?: true

            val html = doc
                .getDocContentForHtmlRange()
                .renderToHtml(theme = addTheme, stripReferences = stripReferences)
            val targetFile = File(htmlDir, doc.fullyQualifiedPath + ".html")
            try {
                targetFile.apply {
                    setWritable(true, false)
                    delete()
                    writeText(html)
                    if (parameters.htmlOutputReadOnly) {
                        setReadOnly()
                    }
                }
            } catch (e: Exception) {
                throw IOException("Could not write to target file $targetFile", e)
            }

            log.lifecycle { "Exported HTML for ${doc.fullyQualifiedPath} to ${targetFile.absolutePath}" }
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
            newDocContent.value.isEmpty() && !sourceHasDocumentation -> range to ""

            // don't create empty kdoc, just remove it altogether
            // We need to expand the replace-range so that the newline is also removed
            newDocContent.value.isEmpty() && sourceHasDocumentation -> {
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
            newDocContent.value.isNotEmpty() && !sourceHasDocumentation -> {
                val newKdoc = buildString {
                    append(newDocContent.toDocText().value)
                    append("\n")
                    append(" ".repeat(docIndent))
                }

                range to newKdoc
            }

            // replace the existing kdoc with the new one
            newDocContent.value.isNotEmpty() && sourceHasDocumentation -> {
                val newKdoc = newDocContent.toDocText(docIndent).value.trimStart()

                range to newKdoc
            }

            else -> error("Unreachable")
        }
    }
}
