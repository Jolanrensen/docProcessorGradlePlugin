package nl.jolanrensen.kdocInclude

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

    internal open class DocumentableWithContent(
        open val documentable: Documentable,
        open val source: DocumentableSource,
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
        open val kdocContent: String? = docComment?.getDocumentString(),
        open val hasInclude: Boolean = docComment?.hasTag(JavadocTag.INCLUDE) ?: false,
    ) {

        val file: File
            get() = File(source.path)

        val textRange = when (docComment) {
            is JavaDocComment -> docComment.comment.textRange
            is KotlinDocComment -> docComment.descriptor.findPsi()?.textRange
            else -> null
        }

        fun withPath(path: String): DocumentableWithContentAndPath =
            DocumentableWithContentAndPath(
                documentable = documentable,
                source = source,
                kdocContent = kdocContent,
                hasInclude = hasInclude,
                path = path,
                logger = logger,
            )
    }

    internal data class DocumentableWithContentAndPath(
        override val documentable: Documentable,
        override val source: DocumentableSource,
        override val kdocContent: String?,
        override val hasInclude: Boolean,
        private val logger: DokkaConsoleLogger,
        val path: String,
    ) : DocumentableWithContent(documentable, source, logger)


    private fun dokkaAnalyse(vararg sourceRoots: File): MutableFullyQualifiedPathTree<DocumentableWithContentAndPath> {
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

        val configuration = DokkaConfigurationImpl(
            sourceSets = listOf(sources),
        )
        val logger = DokkaConsoleLogger()

        val dokkaGenerator = DokkaGenerator(
            configuration = configuration,
            logger = logger,
        )

        val context = dokkaGenerator.initializePlugins(configuration, logger)
        val translators = context[CoreExtensions.sourceToDocumentableTranslator]
            .filter {
                it is DefaultPsiToDocumentableTranslator || // java
                        it is DefaultDescriptorToDocumentableTranslator // kotlin
            }

        val modules = translators.map {
            it.invoke(
                sourceSet = sources,
                context = context,
            )
        }

        val documentables = modules.flatMap {
            it.withDescendants()
                .filter { it.isLinkableElement() && it.hasDocumentation() && it is WithSources }
                .map {
                    val source = (it as WithSources).sources[sources]!!

                    DocumentableWithContent(
                        documentable = it,
                        source = source,
                        logger = logger,
                    )
                }
        }

        return MutableFullyQualifiedPathTree.build {
            for (documentable in documentables) {
                val path = documentable.documentable.dri

                val packagePath = path.packageName?.split(".")?.toTypedArray() ?: emptyArray()
                val classPath = path.classNames?.split(".")?.toTypedArray() ?: emptyArray()
                val callable = path.callable?.name

                packagePart(*packagePath) {
                    clazz(*classPath) {
                        callable(callable) {
                            content = documentable.withPath(this@callable.path)
                        }
                    }
                }
            }
        }
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

        val sourceDocTree = dokkaAnalyse(*sources.toTypedArray())

        // replace @include tags in source kdocs
        var i = 0
        while (sourceDocTree.any { it.content?.hasInclude == true }) {
            if (i++ > 10_000) {
                val circularRefs = sourceDocTree
                    .filter { it.content?.hasInclude == true }
                    .joinToString(",\n") {
                        buildString {
                            appendLine(it.path)
                            appendLine(it.content?.kdocContent?.toKdoc())
                        }
                    }
                error("Circular references detected in @include statements:\n$circularRefs")
            }

            sourceDocTree.filter { it.content?.hasInclude == true }.forEach {
                val kdoc = it.content!!.kdocContent!!
                val processedKdoc = kdoc.replace(includeRegex) { match ->
                    val includePath = match.value.getAtSymbolTargetName("include")
                    val parentPath = (it.parent?.path ?: "")
                    val includeQuery = expandInclude(includePath, parentPath)
                    val queried = sourceDocTree.query(includeQuery)

                    queried?.content?.kdocContent ?: match.value
                }

                it.content = it.content!!.copy(
                    kdocContent = processedKdoc,
                    hasInclude = processedKdoc.contains(includeRegex),
                )
            }
        }

        println(sourceDocTree.query("com.example.plugin.Test"))

        // TODO you now have [DocumentableWithContentAndPath.file and DocumentableWithContentAndPath.textRange]
        // TODO should be good to go from here

        // replace @include tags with matching source kdocs
//        for (source in sources) {
//            for (file in source.walkTopDown()) {
//                if (!file.isFile) continue
//
//                val relativePath = baseDir.get().toPath().relativize(file.toPath())
//                val targetFile = File(target, relativePath.toString())
//                targetFile.parentFile.mkdirs()
//
//                val content = file.readText()
//                val processedContent = TODO()
////                    if (file.extension !in fileExtensions) content
////                    else processFileContent(content, sourceDocsByPackageName)
//
//                targetFile.writeText(processedContent)
//            }
//        }
    }

    /**
     * Scans the given file content for source kdocs and returns a list of them.
     *
     * TODO maybe replace these sources with @define or something similar?
     */
    internal fun readSourceKDocs(fileContent: String): List<SourceKdoc> {

        val packageName = getPackageName(fileContent)

        // Find all kdoc sources that can be targeted with @include
        // This can be any (annotation-less) interface, class, or object
        val sourceKDocs = kdocSourceRegex.findAll(fileContent).map {
            val value = it.value

            val kdocPart = kdocRegex.find(value)!!.value.trim()
            val sourcePart = value.trim().removePrefix(kdocPart).trim().removeSurrounding("\n")
            val sourceName = getSourceName(sourcePart)
            val kdocContent = kdocPart.getKdocContent()
            val hasInclude = kdocContent.split('\n').any { it.startsWith("@include") }

            SourceKdoc(
                packageName = packageName,
                sourceName = sourceName,
                kdocContent = kdocContent,
                hasInclude = hasInclude,
            )
        }.toList()

        return sourceKDocs
    }

    internal fun processFileContent(
        fileContent: String,
        sourceKDocsByPackageName: Map<String, Set<SourceKdoc>>,
    ): String {
        val packageName = getPackageName(fileContent)

        // Find all kdocs and replace @include with the content of the targeted kdoc
        return fileContent.replace(kdocRegex) {
            processKdoc(
                kdoc = it.value,
                sourceKDocsByPackageName = sourceKDocsByPackageName,
                packageName = packageName,
            ) { "" }
        }
    }

    internal fun processKdoc(
        kdoc: String,
        sourceName: String = "",
        sourceKDocsByPackageName: Map<String, Set<SourceKdoc>>,
        packageName: String,
        defaultReplacement: (includeStatement: String) -> String,
    ): String {
        val indent = kdoc.indexOfFirst { it != ' ' }
        val sourceKDocsCurrentPackage = sourceKDocsByPackageName[packageName] ?: emptyList()

        return kdoc
            .getKdocContent()
            .replace(includeRegex) { match ->
                val replacement: String = run {
                    val name = match.value.getAtSymbolTargetName("include")
                    if (name == sourceName || "$packageName.$name" == sourceName)
                        error("Detected a circular reference in @include statements in $packageName.$sourceName:\n$kdoc")

                    // try to get the content using the current package
                    val kdocIncludeContent = sourceKDocsCurrentPackage.firstOrNull { it.sourceName == name }
                    if (kdocIncludeContent != null)
                        return@run kdocIncludeContent.kdocContent

                    // couldn't find the content in the current package and no package was specified,
                    // returning empty string
                    if ('.' !in name)
                        return@run defaultReplacement(match.value)

                    // try to get the content using the specified package
                    val i = name.indexOfLast { it == '.' }
                    val targetPackage = name.subSequence(0, i)
                    val targetName = name.subSequence(i + 1, name.length)

                    println("Looking for $targetName in $targetPackage")

                    val otherKdocIncludeContent = sourceKDocsByPackageName[targetPackage]
                        ?.firstOrNull { it.sourceName == targetName }
                    if (otherKdocIncludeContent != null) {
                        return@run otherKdocIncludeContent.kdocContent
                    }

                    return@run defaultReplacement(match.value)
                }

                replacement
            }
            .toKdoc(indent)
    }
}