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

        // gather source kdocs
        val sourceDocsByPackageName = buildMap<String, MutableSet<SourceKdoc>> {
            for (source in sources) {
                for (file in source.walkTopDown()) {
                    if (!file.isFile) continue

                    val content = file.readText()
                    val sourceKDocs = readSourceKDocs(content)

                    for (sourceKDoc in sourceKDocs) {
                        getOrPut(sourceKDoc.packageName) { mutableSetOf() }.add(sourceKDoc)
                    }
                }
            }
        }

        // replace @include tags in source kdocs
        var i = 0
        while (sourceDocsByPackageName.any { it.value.any { it.hasInclude } }) {
            if (i++ > 10_000) {
                val circularRefs = sourceDocsByPackageName
                    .flatMap { it.value.filter { it.hasInclude } }
                    .joinToString(",\n") {
                        buildString {
                            appendLine("${it.packageName}.${it.sourceName}:")
                            appendLine(it.kdocContent.toKdoc())
                        }
                    }
                error("Circular references detected in @include statements:\n$circularRefs")
            }
            sourceDocsByPackageName.values.first { set ->
                val found = set.firstOrNull { it.hasInclude }

                if (found != null) {
                    set.remove(found)

                    val new = processKdoc(
                        kdoc = found.kdocContent.removeSuffix("\n").toKdoc(),
                        sourceName = found.sourceName,
                        sourceKDocsByPackageName = sourceDocsByPackageName,
                        packageName = found.packageName,
                    ) { it }.getKdocContent()
                        .removeSuffix("\n")

                    set.add(
                        found.copy(
                            kdocContent = new,
                            hasInclude = new.split('\n').any { it.startsWith("@include") },
                        )
                    )
                }

                found != null
            }
        }

        // replace @include tags with matching source kdocs
        for (source in sources) {
            for (file in source.walkTopDown()) {
                if (!file.isFile) continue

                val relativePath = baseDir.get().toPath().relativize(file.toPath())
                val targetFile = File(target, relativePath.toString())
                targetFile.parentFile.mkdirs()

                val content = file.readText()
                val processedContent =
                    if (file.extension !in fileExtensions) content
                    else processFileContent(content, sourceDocsByPackageName)

                targetFile.writeText(processedContent)
            }
        }
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

            val kdocPart = kdocRegex.find(value)!!.value.trim().removeSurrounding("\n")
            val sourcePart = value.removePrefix(kdocPart).trim().removeSurrounding("\n")
            val sourceName = getSourceName(sourcePart)
            val kdocContent = kdocPart.getKdocContent()
            val hasInclude = kdocContent.split('\n').any { it.startsWith("@include") }

            SourceKdoc(packageName, sourceName, kdocContent, hasInclude)
        }.toList()

        return sourceKDocs
    }

    internal fun processFileContent(
        fileContent: String,
        sourceKDocsByPackageName: Map<String, Set<SourceKdoc>>
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

    /**
     * Get include target name.
     * For instance, changes `@include [Foo]` to `Foo`
     */
    internal fun String.getIncludeTargetName(): String {
        require("@include" in this)

        return this
            .trim()
            .removePrefix("@include")
            .trim()
            .removePrefix("[")
            .removeSuffix("]")
            .trim()
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
                    val name = match.value.getIncludeTargetName()
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