package com.example.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
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

    private val kdocSourceRegex = Regex("""( *)/\*\*([^*]|\*(?!/))*?\*/(\s+)(.*)(interface|class|object)(\s+).+""")
    private val kdocRegex = Regex("""( *)/\*\*([^*]|\*(?!/))*?\*/""")
    private val includeRegex = Regex("""@include(\s+)(\[?)(.+)(]?)""")

    internal data class SourceKdoc(val packageName: String, val source: String, val kdocContent: String)

    @TaskAction
    fun process() {
        println("Hello from plugin 'com.example.plugin.kdocInclude'")

        val fileExtensions = fileExtensions.get()
        val sources = sources.get()
        val target = target.get()

        if (target.exists()) target.deleteRecursively()
        target.mkdir()

        println("Using target folder: $target")
        println("Using source folders: $sources")
        println("Using file extensions: $fileExtensions")

        // gather source kdocs
        val sourceDocs = buildMap<String, MutableList<SourceKdoc>> {
            for (source in sources) {
                for (file in source.walkTopDown()) {
                    if (!file.isFile) continue

                    val content = file.readText()
                    val sourceKDocs = readSourceKDocs(content)

                    for (sourceKDoc in sourceKDocs) {
                        getOrPut(sourceKDoc.packageName) { mutableListOf() }.add(sourceKDoc)
                    }
                }
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
                    else processFileContent(content, sourceDocs)

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

            val kdocPart = kdocRegex.find(value)!!.value
            val sourcePart = value.removePrefix(kdocPart).trim()
            val sourceName = getSourceName(sourcePart)

            val kdocContent = kdocPart.getKdocContent()

            SourceKdoc(packageName, sourceName, kdocContent)
        }.toList()

        return sourceKDocs
    }

    internal fun processFileContent(fileContent: String, sourceKDocs: Map<String, List<SourceKdoc>>): String {
        val packageName = getPackageName(fileContent)

        // Find all kdocs and replace @include with the content of the targeted kdoc
        return fileContent.replace(kdocRegex) {
            processKdoc(it.value, sourceKDocs, packageName)
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

    internal fun processKdoc(kdoc: String, sourceKDocs: Map<String, List<SourceKdoc>>, packageName: String): String {
        val indent = kdoc.indexOfFirst { it != ' ' }

        val sourceKDocsCurrentPackage = sourceKDocs[packageName] ?: emptyList()

        return kdoc
            .getKdocContent()
            .replace(includeRegex) { match ->
                val name = match.value.getIncludeTargetName()

                // try to get the content using the current package
                val kdocContent = sourceKDocsCurrentPackage.firstOrNull { name in it.source }?.kdocContent
                if (kdocContent != null) return@replace kdocContent

                // couldn't find the content in the current package and no package was specified,
                // returning empty string
                if ('.' !in name) return@replace ""

                // try to get the content using the specified package
                val i = name.indexOfLast { it == '.' }
                val targetPackage = name.subSequence(0, i)
                val targetName = name.subSequence(i + 1, name.length)

                println("Looking for $targetName in $targetPackage")

                val otherKdocContent = sourceKDocs[targetPackage]?.firstOrNull { targetName in it.source }?.kdocContent

                return@replace otherKdocContent
                    ?: ""
            }
            .toKdoc(indent)
    }
}