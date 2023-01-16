package nl.jolanrensen.kdocInclude

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaGenerator
import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.base.translators.descriptors.DefaultDescriptorToDocumentableTranslator
import org.jetbrains.dokka.base.translators.psi.DefaultPsiToDocumentableTranslator
import org.jetbrains.dokka.gradle.GradleDokkaSourceSetBuilder
import org.jetbrains.dokka.model.WithSources
import org.jetbrains.dokka.model.withDescendants
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import java.io.File
import javax.inject.Inject

open class ProcessKdocIncludeTask @Inject constructor(factory: ObjectFactory) : DefaultTask() {

    /**
     * Source root folders for preprocessing
     */
    @get:InputFiles
    val sources: ListProperty<File> = factory
        .listProperty(File::class.java)

    /**
     * Set base directory which will be used for relative source paths.
     * By default, it is '$projectDir'.
     */
    @get:Input
    val baseDir: Property<File> = factory
        .property(File::class.java)
        .convention(project.projectDir)

    /**
     * List of file extensions to be included into preprocessing.
     * By default: kt, kts
     */
    @get:Input
    val fileExtensions: ListProperty<String> = factory
        .listProperty(String::class.java)
        .convention(listOf("kt", "kts"))

    /**
     * Target folder to place preprocessing result in regular source processing
     * phase.
     */
    @get:Input
    val target: Property<File> = factory
        .property(File::class.java)
        .convention(File(project.buildDir, "kdocInclude${File.separatorChar}${taskIdentity.name}"))

    @get:OutputFiles
    val targets: FileCollection = factory.fileCollection()

    private val includeRegex = Regex("""@include(\s+)(\[?)(.+)(]?)""")

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun process() {
        println("Hello from plugin 'nl.jolanrensen.kdocInclude'")

        val fileExtensions = fileExtensions.get()
        val sources = sources.get()
        val target = target.get()

        val relativeSources = sources.map { it.relativeTo(baseDir.get()) }
        (targets as ConfigurableFileCollection).setFrom(
            relativeSources.map {
                File(target, it.path)
            }
        )

        if (target.exists()) target.deleteRecursively()
        target.mkdir()

        println("Using target folder: $target")
        println("Using source folders: $sources")
        println("Using target folders: ${targets.files.toList()}")
        println("Using file extensions: $fileExtensions")

        // analyse the sources with dokka to get the documentables
        val sourceDocs = analyseSourcesWithDokka(sources)

        // replace @include tags
        val modifiedDocumentables = replaceIncludeTags(sourceDocs)

        // filter to only include the modified documentables
        val modifiedDocumentablesPerFile = getModifiedDocumentablesPerFile(modifiedDocumentables)

        // copy the sources to the target folder while replacing all docs in modified documentables
        copyAndModifySources(sources, target, modifiedDocumentablesPerFile)
    }

    private fun analyseSourcesWithDokka(sourceRoots: List<File>): Map<String, DocumentableWithSource> {
        // gather sources for dokka
        val sourceSetName = "sourceSet"
        val sources = GradleDokkaSourceSetBuilder(
            name = sourceSetName,
            project = project,
            sourceSetIdFactory = { DokkaSourceSetID(it, sourceSetName) },
        ).apply {
            sourceRoots.forEach {
                sourceRoot(it)
            }
        }.build()

        // initialize dokka with the sources
        val configuration = DokkaConfigurationImpl(
            sourceSets = listOf(sources),
        )
        val logger = DokkaConsoleLogger()
        val dokkaGenerator = DokkaGenerator(
            configuration = configuration,
            logger = logger,
        )

        // get the sourceToDocumentableTranslators from DokkaBase, both for java and kotlin files
        val context = dokkaGenerator.initializePlugins(configuration, logger)
        val translators = context[CoreExtensions.sourceToDocumentableTranslator]
            .filter {
                it is DefaultPsiToDocumentableTranslator || // java
                        it is DefaultDescriptorToDocumentableTranslator // kotlin
            }

        // execute the translators on the sources to gather the modules
        val modules = translators.map {
            it.invoke(
                sourceSet = sources,
                context = context,
            )
        }

        // collect the right documentables from the modules (only linkable elements with docs)
        val documentables = modules.flatMap {
            it.withDescendants()
                .filter { it.isLinkableElement() && it.hasDocumentation() && it is WithSources }
                .map {
                    val source = (it as WithSources).sources[sources]!!
                    DocumentableWithSource(
                        documentable = it,
                        source = source,
                        logger = logger,
                    )
                }
        }

        return documentables.associateBy { it.path }
    }

    private fun replaceIncludeTags(sourceDocs: Map<String, DocumentableWithSource>): Map<String, DocumentableWithSource> {
        val mutableSourceDocs = sourceDocs.toMutableMap()

        var i = 0
        while (mutableSourceDocs.any { it.value.hasInclude }) {
            if (i++ > 10_000) {
                val circularRefs = mutableSourceDocs
                    .filter { it.value.hasInclude }
                    .entries
                    .joinToString(",\n") { (path, content) ->
                        buildString {
                            appendLine(path)
                            appendLine(content.kdocContent?.toKdoc())
                        }
                    }
                error("Circular references detected in @include statements:\n$circularRefs")
            }

            mutableSourceDocs
                .filter { it.value.hasInclude }
                .forEach { (path, content) ->
                    val kdoc = content.kdocContent!!
                    val processedKdoc = kdoc.replace(includeRegex) { match ->
                        // get the full include path
                        val includePath = match.value.getAtSymbolTargetName("include")
                        val parentPath = path.take(path.lastIndexOf('.').coerceAtLeast(0))
                        val includeQuery = expandInclude(includePath, parentPath)

                        // query the tree for the include path
                        val queried = mutableSourceDocs[includeQuery]

                        // replace the include statement with the kdoc of the queried node (if found)
                        queried?.kdocContent ?: match.value
                    }

                    val wasModified = kdoc != processedKdoc

                    if (wasModified) {
                        mutableSourceDocs[path] = content.copy(
                            kdocContent = processedKdoc,
                            hasInclude = processedKdoc.contains(includeRegex),
                            wasModified = true,
                        )
                    }
                }
        }
        return mutableSourceDocs
    }

    private fun getModifiedDocumentablesPerFile(modifiedSourceDocs: Map<String, DocumentableWithSource>): Map<File, List<DocumentableWithSource>> =
        modifiedSourceDocs
            .filter { it.value.wasModified }
            .entries
            .groupBy { it.value.file }
            .mapValues { (_, nodes) ->
                nodes.map { it.value }
            }

    private fun copyAndModifySources(
        sources: MutableList<File>,
        target: File?,
        modifiedDocumentablesPerFile: Map<File, List<DocumentableWithSource>>
    ) {
        for (source in sources) {
            for (file in source.walkTopDown()) {
                if (!file.isFile) continue

                val relativePath = baseDir.get().toPath().relativize(file.toPath())
                val targetFile = File(target, relativePath.toString())
                targetFile.parentFile.mkdirs()

                val content = file.readText()
                val modifications = modifiedDocumentablesPerFile[file] ?: emptyList()

                val modificationsByRange = modifications
                    .groupBy { it.textRange }
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