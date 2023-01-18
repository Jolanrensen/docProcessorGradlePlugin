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

private val includeRegex = Regex("""@include(\s+)(\[?)(.+)(]?)""")

abstract class ProcessKdocIncludeAction : WorkAction<ProcessKdocIncludeAction.Parameters> {
    interface Parameters : WorkParameters {
        var baseDir: File
        var sources: DokkaSourceSetImpl
        var sourceRoots: MutableList<File>
        var target: File?
        var debug: Boolean
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

        // replace @include tags
        val modifiedDocumentables = replaceIncludeTags(sourceDocs)

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

    private fun replaceIncludeTags(sourceDocs: Map<String, List<DocumentableWithSource>>): Map<String, List<DocumentableWithSource>> {
        val mutableSourceDocs = sourceDocs
            .mapValues { (_, docs) -> docs.toMutableList() }

        var i = 0
        while (mutableSourceDocs.any { it.value.any { it.hasInclude } }) {
            if (i++ > 10_000) {
                val circularRefs = mutableSourceDocs
                    .filter { it.value.any { it.hasInclude } }
                    .entries
                    .joinToString(",\n") { (path, documentables) ->
                        buildString {
                            appendLine("`$path`:")
                            appendLine(documentables.map { it.kdocContent?.toKdoc() })
                        }
                    }
                error("Circular references detected in @include statements:\n$circularRefs")
            }

            mutableSourceDocs
                .filter { it.value.any { it.hasInclude } }
                .forEach { (path, documentables) ->
                    documentables.replaceAll { documentable ->
                        val kdoc = documentable.kdocContent!!
                        val processedKdoc = kdoc.replace(includeRegex) { match ->
                            // get the full include path
                            val includePath = match.value.getAtSymbolTargetName("include")
                            val parentPath = path.take(path.lastIndexOf('.').coerceAtLeast(0))
                            val includeQuery = expandInclude(includePath, parentPath)

                            // query the tree for the include path
                            val queried = mutableSourceDocs[includeQuery]

                            // detect self-reference
                            if (queried?.all { it == documentable } == true) {
                                val circularRef = buildString {
                                    appendLine("`$path`:")
                                    appendLine(documentable.kdocContent.toKdoc())
                                }
                                error("Self-reference detected in @include statement:\n$circularRef")
                            }

                            // replace the include statement with the kdoc of the queried node (if found)
                            queried
                                ?.firstOrNull { it != documentable }
                                ?.kdocContent
                                ?: match.value
                        }

                        val wasModified = kdoc != processedKdoc

                        if (wasModified) {
                            documentable.copy(
                                kdocContent = processedKdoc,
                                hasInclude = processedKdoc.contains(includeRegex),
                                wasModified = true,
                            )
                        } else {
                            documentable
                        }
                    }
                }
        }
        return mutableSourceDocs
    }

    private fun getModifiedDocumentablesPerFile(
        modifiedSourceDocs: Map<String, List<DocumentableWithSource>>,
    ): Map<File, List<DocumentableWithSource>> =
        modifiedSourceDocs
            .entries
            .flatMap { it.value.filter { it.wasModified } }
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
                    .groupBy { it.textRange!! }
                    .mapValues { it.value.single().let { Pair(it.kdocContent!!, it.indent) } }
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




