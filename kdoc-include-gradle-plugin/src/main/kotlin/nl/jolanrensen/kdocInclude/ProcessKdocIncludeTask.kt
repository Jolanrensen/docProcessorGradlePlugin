package nl.jolanrensen.kdocInclude

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiNamedElement
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
import org.jetbrains.dokka.analysis.DescriptorDocumentableSource
import org.jetbrains.dokka.analysis.PsiDocumentableSource
import org.jetbrains.dokka.base.translators.descriptors.DefaultDescriptorToDocumentableTranslator
import org.jetbrains.dokka.base.translators.psi.DefaultPsiToDocumentableTranslator
import org.jetbrains.dokka.gradle.GradleDokkaSourceSetBuilder
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.model.WithSources
import org.jetbrains.dokka.model.withDescendants
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
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
    public val targets: FileCollection = factory.fileCollection()

    private val kdocSourceRegex =
        Regex("""( *)/\*\*([^*]|\*(?!/))*?\*/((\s)|(@.+))+(.*)(interface|class|object)(\s+).+""")
    private val kdocRegex = Regex("""( *)/\*\*([^*]|\*(?!/))*?\*/""")
    private val includeRegex = Regex("""@include(\s+)(\[?)(.+)(]?)""")

    internal data class SourceKdoc(
        val packageName: String,
        val sourceName: String,
        val kdocContent: String,
        val hasInclude: Boolean,
    )

    init {
        outputs.upToDateWhen { false }
    }

    internal data class DocumentableWithSource(
        val documentable: Documentable,
        val source: DocumentableSource,
        private val logger: DokkaConsoleLogger,
        val docComment: DocComment? = findClosestDocComment(
            element = source.let { s ->
                when (s) {
                    is PsiDocumentableSource -> s.psi
                    is DescriptorDocumentableSource -> s.descriptor.findPsi() as PsiNamedElement
                    else -> null
                }
            },
            logger = logger,
        ),
        val path: String = documentable.dri.path,
        val file: File = File(source.path),
        val fileText: String = file.readText(),
        val textRange: TextRange = run {
            val ogRange = when (docComment) {
                is JavaDocComment -> docComment.comment.textRange // includes just comment
                is KotlinDocComment -> docComment.descriptor.findPsi()!!.textRange // includes comment and descriptor
                else -> error("Unknown doc comment type")
            }
            val query = ogRange.substring(fileText)
            val startComment = query.indexOf("/**")
            val endComment = query.indexOf("*/")

            require(startComment != -1) { "Could not find start of comment" }
            require(endComment != -1) { "Could not find end of comment" }

            TextRange(ogRange.startOffset + startComment, ogRange.startOffset + endComment + 2)
        },

        val indent: Int = textRange.startOffset - fileText.lastIndexOfNot('\n', textRange.startOffset),
        val kdocContent: String? = docComment?.getDocumentString(),
        val hasInclude: Boolean = docComment?.hasTag(JavadocTag.INCLUDE) ?: false,
        val wasModified: Boolean = false,
    ) {
        fun queryFile(): String = textRange.substring(file.readText())
    }

    private fun dokkaAnalyse(vararg sourceRoots: File): MutableMap<String, DocumentableWithSource> {
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
                    val doc = DocumentableWithSource(
                        documentable = it,
                        source = source,
                        logger = logger,
                    )
                    doc
                }
        }

        return documentables.associateBy { it.path }.toMutableMap()

//        // convert the documentables to a tree for easier access and return
//        return FullyQualifiedPathTree.build {
//            for (documentable in documentables) {
//                val path = documentable.documentable.dri
//
//                val packagePath = path.packageName?.split(".")?.toTypedArray() ?: emptyArray()
//                val classPath = path.classNames?.split(".")?.toTypedArray() ?: emptyArray()
//                val callable = path.callable?.name
//
//                packagePart(*packagePath) {
//                    clazz(*classPath) {
//                        callable(callable) {
//                            content = documentable
//                        }
//                    }
//                }
//            }
//        }
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

        val sourceDocs = dokkaAnalyse(*sources.toTypedArray())

        // replace @include tags in kdoc tree
        var i = 0
        while (sourceDocs.any { it.value.hasInclude }) {
            if (i++ > 10_000) {
                val circularRefs = sourceDocs
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

            sourceDocs
                .filter { it.value.hasInclude }
                .forEach { (path, content) ->
                    val kdoc = content.kdocContent!!
                    val processedKdoc = kdoc.replace(includeRegex) { match ->
                        // get the full include path
                        val includePath = match.value.getAtSymbolTargetName("include")
                        val parentPath = path.take(path.lastIndexOf('.').coerceAtLeast(0))
                        val includeQuery = expandInclude(includePath, parentPath)

                        // query the tree for the include path
                        val queried = sourceDocs[includeQuery]

                        // replace the include statement with the kdoc of the queried node (if found)
                        queried?.kdocContent ?: match.value
                    }

                    val wasModified = kdoc != processedKdoc

                    if (wasModified) {
                        sourceDocs[path] = content.copy(
                            kdocContent = processedKdoc,
                            hasInclude = processedKdoc.contains(includeRegex),
                            wasModified = true,
                        )
                    }
                }
        }

        println(sourceDocs["com.example.plugin.Test"])

        // TODO you now have [DocumentableWithContentAndPath.file and DocumentableWithContentAndPath.textRange]
        // TODO should be good to go from here

        val modifiedKDocsPerFile = sourceDocs
            .filter { it.value.wasModified }
            .entries
            .groupBy { it.value.file }
            .mapValues { (_, nodes) ->
                nodes.map { it.value }
            }

        // replace @include tags with matching source kdocs
        for (source in sources) {
            for (file in source.walkTopDown()) {
                if (!file.isFile) continue

                val relativePath = baseDir.get().toPath().relativize(file.toPath())
                val targetFile = File(target, relativePath.toString())
                targetFile.parentFile.mkdirs()

                val content = file.readText()
                val modifications = modifiedKDocsPerFile[file] ?: emptyList()

                val modificationsByRange = modifications
                    .groupBy { it.textRange }
                    .mapValues { it.value.single().let { Pair(it.kdocContent!!, it.indent) } }
                    .toSortedMap(compareBy { it.startOffset })
                    .map { (textRange, kdocAndIndent) ->
                        val (kdoc, indent) = kdocAndIndent
                        val newKdoc = kdoc.toKdoc(indent).trimStart()

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

//    /**
//     * Scans the given file content for source kdocs and returns a list of them.
//     *
//     * TODO maybe replace these sources with @define or something similar?
//     */
//    internal fun readSourceKDocs(fileContent: String): List<SourceKdoc> {
//
//        val packageName = getPackageName(fileContent)
//
//        // Find all kdoc sources that can be targeted with @include
//        // This can be any (annotation-less) interface, class, or object
//        val sourceKDocs = kdocSourceRegex.findAll(fileContent).map {
//            val value = it.value
//
//            val kdocPart = kdocRegex.find(value)!!.value.trim()
//            val sourcePart = value.trim().removePrefix(kdocPart).trim().removeSurrounding("\n")
//            val sourceName = getSourceName(sourcePart)
//            val kdocContent = kdocPart.getKdocContent()
//            val hasInclude = kdocContent.split('\n').any { it.startsWith("@include") }
//
//            SourceKdoc(
//                packageName = packageName,
//                sourceName = sourceName,
//                kdocContent = kdocContent,
//                hasInclude = hasInclude,
//            )
//        }.toList()
//
//        return sourceKDocs
//    }
//
//    internal fun processFileContent(
//        fileContent: String,
//        sourceKDocsByPackageName: Map<String, Set<SourceKdoc>>,
//    ): String {
//        val packageName = getPackageName(fileContent)
//
//        // Find all kdocs and replace @include with the content of the targeted kdoc
//        return fileContent.replace(kdocRegex) {
//            processKdoc(
//                kdoc = it.value,
//                sourceKDocsByPackageName = sourceKDocsByPackageName,
//                packageName = packageName,
//            ) { "" }
//        }
//    }
//
//    internal fun processKdoc(
//        kdoc: String,
//        sourceName: String = "",
//        sourceKDocsByPackageName: Map<String, Set<SourceKdoc>>,
//        packageName: String,
//        defaultReplacement: (includeStatement: String) -> String,
//    ): String {
//        val indent = kdoc.indexOfFirst { it != ' ' }
//        val sourceKDocsCurrentPackage = sourceKDocsByPackageName[packageName] ?: emptyList()
//
//        return kdoc
//            .getKdocContent()
//            .replace(includeRegex) { match ->
//                val replacement: String = run {
//                    val name = match.value.getAtSymbolTargetName("include")
//                    if (name == sourceName || "$packageName.$name" == sourceName)
//                        error("Detected a circular reference in @include statements in $packageName.$sourceName:\n$kdoc")
//
//                    // try to get the content using the current package
//                    val kdocIncludeContent = sourceKDocsCurrentPackage.firstOrNull { it.sourceName == name }
//                    if (kdocIncludeContent != null)
//                        return@run kdocIncludeContent.kdocContent
//
//                    // couldn't find the content in the current package and no package was specified,
//                    // returning empty string
//                    if ('.' !in name)
//                        return@run defaultReplacement(match.value)
//
//                    // try to get the content using the specified package
//                    val i = name.indexOfLast { it == '.' }
//                    val targetPackage = name.subSequence(0, i)
//                    val targetName = name.subSequence(i + 1, name.length)
//
//                    println("Looking for $targetName in $targetPackage")
//
//                    val otherKdocIncludeContent = sourceKDocsByPackageName[targetPackage]
//                        ?.firstOrNull { it.sourceName == targetName }
//                    if (otherKdocIncludeContent != null) {
//                        return@run otherKdocIncludeContent.kdocContent
//                    }
//
//                    return@run defaultReplacement(match.value)
//                }
//
//                replacement
//            }
//            .toKdoc(indent)
//    }
}

/**
 * Last index of not [char] moving from startIndex down to 0.
 * Returns 0 if char is not found (since last index looked at is 0).
 * Returns `this.size` if [char] is found at [startIndex].
 *
 * @receiver
 * @param char Char
 * @param startIndex Start index
 * @return
 */
private fun String.lastIndexOfNot(char: Char, startIndex: Int = lastIndex): Int {
    for (i in startIndex downTo 0) {
        if (this[i] == char) return i + 1
    }
    return 0
}