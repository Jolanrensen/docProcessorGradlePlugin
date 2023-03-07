@file:Suppress("LeakingThis")

package nl.jolanrensen.docProcessor

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import java.io.File
import java.io.FileWriter
import java.io.IOException

abstract class DocProcessorFunctionalTest(name: String) {

    init {
        println("NOTE!! make sure you have the plugin installed in your local maven repo")
    }

    @Language("kts")
    private val settingsFile = """
        pluginManagement {
            repositories {
                mavenLocal()
                gradlePluginPortal()
                mavenCentral()
            }
        }
    """.trimIndent()

    @Language("kts")
    private fun getBuildFileContent(processors: List<String>): String = """
        import nl.jolanrensen.docProcessor.gradle.*
        import nl.jolanrensen.docProcessor.defaultProcessors.*

        plugins {  
            kotlin("jvm") version "1.8.0"
            id("nl.jolanrensen.docProcessor") version "1.0-SNAPSHOT"
        }
        
        val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
        
        val processKdocMain by creatingProcessDocTask(sources = kotlinMainSources) {
            debug = true
            processors = listOf(${processors.joinToString()})
        }
        
        tasks.compileKotlin { dependsOn(processKdocMain) }
    """.trimIndent()

    val projectDirectory = File("build/$name")
    val outputDirectory = File(projectDirectory, "build/docProcessor/processKdocMain")

    enum class Language {
        JAVA, KOTLIN
    }

    fun createUniqueFileName(
        directory: File,
        language: Language,
    ): String {
        val fileExtension = when (language) {
            Language.JAVA -> "java"
            Language.KOTLIN -> "kt"
        }

        var i = 0
        while (true) {
            val fileName = "Test$i.$fileExtension"
            if (!File(directory, fileName).exists()) return fileName
            i++
        }
    }

    class AdditionalFile(
        val relativePath: String = "src/main/kotlin/com/example/plugin/Test.kt",
        val content: String,
    )

    /**
     * This function allows you to process content with any given processors.
     * Make sure to enter the [packageName] and [fileName] correctly, since this is
     * where the temporary file will be created.
     *
     * @param content Content of the file to be processed
     * @param packageName Package name of the file to be processed
     * @param fileName File name of the file to be processed
     * @param language Language of the file to be processed
     * @param processors Processors to be used
     * @param additionalFiles [Additional files][AdditionalFile] to be created in the project that are
     *   processed but not returned
     * @return The processed content of [content]
     */
    @kotlin.jvm.Throws(IOException::class)
    fun processContent(
        content: String,
        packageName: String,
        fileName: String = "Test",
        language: Language = Language.KOTLIN,
        processors: List<String>,
        additionalFiles: List<AdditionalFile> = emptyList(),
    ): String {
        initializeProjectFiles(processors)

        for (additionalFile in additionalFiles) {
            File(projectDirectory, additionalFile.relativePath)
                .write(additionalFile.content)
        }

        val relativePath = buildString {
            append("src/main/")
            append(
                when (language) {
                    Language.JAVA -> "java/"
                    Language.KOTLIN -> "kotlin/"
                }
            )
            append(packageName.replace('.', '/'))
        }
        val directory = File(projectDirectory, relativePath)

        val extension = when (language) {
            Language.JAVA -> "java"
            Language.KOTLIN -> "kt"
        }
        val fileNameWithExtension = "$fileName.$extension"

        File(directory, fileNameWithExtension)
            .write(content)

        runBuild()

        return File(File(outputDirectory, relativePath), fileNameWithExtension).readText()
    }

    private fun initializeProjectFiles(processors: List<String>) {
        // Set up the test build
        projectDirectory.deleteRecursively()
        projectDirectory.mkdirs()

        File(projectDirectory, "settings.gradle.kts")
            .write(settingsFile)

        File(projectDirectory, "build.gradle.kts")
            .write(getBuildFileContent(processors))
    }

    private fun runBuild(): BuildResult =
        GradleRunner.create()
            .forwardOutput()
            .withArguments("processKdocMain")
            .withProjectDir(projectDirectory)
            .withDebug(true)
            .build()

    @Throws(IOException::class, SecurityException::class)
    private fun File.write(string: String) {
        if (!parentFile.exists()) {
            val result = parentFile.mkdirs()
            if (!result) {
                throw IOException("Could not create parent directories for $this")
            }
        }

        FileWriter(this).use { writer -> writer.write(string) }
    }
}