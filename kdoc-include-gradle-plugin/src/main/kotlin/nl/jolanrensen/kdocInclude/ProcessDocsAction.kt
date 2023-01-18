package nl.jolanrensen.kdocInclude

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
import java.io.Serializable


fun interface DocsProcessor : Serializable {

    /**
     * Process given [docsByPath].
     * You can use [DocumentableWithSource.copy] to create a new [DocumentableWithSource] with modified
     * [DocumentableWithSource.kdocContent].
     * Mark the docs that were modified with [DocumentableWithSource.isModified] and
     * don't forget to update [DocumentableWithSource.tags] accordingly.
     *
     * @param docsByPath Docs by path
     * @return modified docs by path
     */
    fun process(docsByPath: Map<String, List<DocumentableWithSource>>): Map<String, List<DocumentableWithSource>>
}

abstract class ProcessDocsAction : WorkAction<ProcessDocsAction.Parameters> {
    interface Parameters : WorkParameters {
        var baseDir: File
        var sources: DokkaSourceSetImpl
        var sourceRoots: List<File>
        var target: File?
        var debug: Boolean
        var processors: Set<DocsProcessor>
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

        // Run all processors
        val modifiedDocumentables =
            parameters.processors.fold(sourceDocs) { acc, processor ->
                processor.process(acc)
            }

        // filter to only include the modified documentables
        val modifiedDocumentablesPerFile = getModifiedDocumentablesPerFile(modifiedDocumentables)

        println("Modified documentables: ${modifiedDocumentablesPerFile.values.flatMap { it.map { it.path } }}")

        // copy the sources to the target folder while replacing all docs in modified documentables
        copyAndModifySources(modifiedDocumentablesPerFile)
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
        val documentables = modules.flatMap {
            it.withDescendants()
                .filter { it.isLinkableElement() && it.hasDocumentation() && it is WithSources }
                .mapNotNull {
                    val source = (it as WithSources).sources[parameters.sources]!!

                    DocumentableWithSource.createOrNull(
                        documentable = it,
                        source = source,
                        logger = logger,
                    )
                }
        }

        return documentables.groupBy { it.path }
    }

    private fun getModifiedDocumentablesPerFile(
        modifiedSourceDocs: Map<String, List<DocumentableWithSource>>,
    ): Map<File, List<DocumentableWithSource>> =
        modifiedSourceDocs
            .entries
            .flatMap { it.value.filter { it.isModified } }
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
                    .groupBy { it.textRange }
                    .mapValues { it.value.single().let { Pair(it.kdocContent, it.indent) } }
                    .toSortedMap(compareBy { it.startOffset })
                    .map { (textRange, kdocAndIndent) ->
                        val (kdoc, indent) = kdocAndIndent

                        var fixedKdoc = kdoc.trim()

                        // fix start and end newlines of kdoc
                        if (fixedKdoc.split('\n').size > 1) {
                            if (fixedKdoc.first() != '\n') fixedKdoc = "\n$fixedKdoc"
                            if (fixedKdoc.last() != '\n') fixedKdoc = "$fixedKdoc\n"
                        }

                        val newKdoc = fixedKdoc.toKdoc(indent).trimStart()

                        val range = textRange.startOffset until textRange.endOffset
                        range to newKdoc
                    }.toMap()

                val fileRange = content.indices.associateWith { content[it].toString() }.toMutableMap()
                for ((range, kdoc) in modificationsByRange) {
                    range.forEach { fileRange.remove(it) }
                    fileRange[range.first] = kdoc
                }

                val processedContent = fileRange.toSortedMap().values.joinToString("")
                targetFile.writeText(processedContent)
            }
        }
    }


}




