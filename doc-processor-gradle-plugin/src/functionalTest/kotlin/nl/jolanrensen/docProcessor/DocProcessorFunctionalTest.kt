@file:Suppress("LeakingThis")

package nl.jolanrensen.docProcessor

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import java.io.File
import java.io.FileWriter
import java.io.IOException

abstract class DocProcessorFunctionalTest(name: String) {

    protected val version = "0.3.3"

    init {
        println("NOTE!! make sure you have the plugin installed in your local maven repo")
    }

    @Language("kt")
    val annotationDef = """
        package com.example.plugin
        
        import kotlin.annotation.AnnotationTarget.*
        
        @Target(
            CLASS,
            ANNOTATION_CLASS,
            TYPE_PARAMETER,
            PROPERTY,
            FIELD,
            LOCAL_VARIABLE,
            VALUE_PARAMETER,
            CONSTRUCTOR,
            FUNCTION,
            PROPERTY_GETTER,
            PROPERTY_SETTER,
            TYPE,
            TYPEALIAS,
            FILE,
        )
        annotation class ${ExcludeFromSources::class.simpleName}""".trimIndent()

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
    private fun getBuildFileContent(processors: List<String>, plugins: List<String>): String = """
        import nl.jolanrensen.docProcessor.gradle.*
        import nl.jolanrensen.docProcessor.defaultProcessors.*
        
        plugins {  
            kotlin("jvm") version "1.9.21"
            id("nl.jolanrensen.docProcessor") version "$version"
        }
        
        repositories {
            mavenLocal()
        }
        
        val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
        
        val processKdocMain by creatingProcessDocTask(sources = kotlinMainSources) {
            
            dependencies {
                ${if (plugins.isEmpty()) "" else """
                    ${plugins.joinToString("\n") { "plugin(\"$it\")" }}
                """.trimIndent()}
            }
           
            arguments += ARG_DOC_PROCESSOR_LOG_NOT_FOUND to false
            
            processors = listOf(${processors.joinToString()})
        }
        
        tasks.compileKotlin { dependsOn(processKdocMain) }
        
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions.jvmTarget = "1.8"
        }
        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(8))
            }
        }
    """.trimIndent()

    private val projectDirectory = File("build/$name")
    private val outputDirectory = File(projectDirectory, "build/docProcessor/processKdocMain")

    enum class FileLanguage {
        JAVA, KOTLIN
    }

    sealed interface Additional {
        val relativePath: String
    }

    class AdditionalFile(
        override val relativePath: String = "src/main/kotlin/com/example/plugin/Test.kt",
        val content: String,
    ) : Additional

    class AdditionalDirectory(
        override val relativePath: String = "src/main/kotlin/com/example/plugin",
    ) : Additional


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
     * @param additionals [Additional files][AdditionalFile] to be created in the project that are
     *   processed but not returned
     * @return The processed content of [content]
     */
    @kotlin.jvm.Throws(IOException::class)
    fun processContent(
        content: String,
        packageName: String,
        fileName: String = "Test",
        language: FileLanguage = FileLanguage.KOTLIN,
        processors: List<String>,
        plugins: List<String> = emptyList(),
        additionals: List<Additional> = emptyList(),
    ): String {
        initializeProjectFiles(processors = processors, plugins = plugins)
        writeAdditionalFiles(additionals)

        // Get source- and destination directories based on the package name
        val relativePath = getRelativePath(language, packageName)
        val sourceDirectory = File(projectDirectory, relativePath)
        val destinationDirectory = File(outputDirectory, relativePath)

        val fileNameWithExtension = getFileNameWithExtension(fileName, language)

        val sourceFile = File(sourceDirectory, fileNameWithExtension)
        sourceFile.write(content)

        runBuild()

        val destinationFile = File(destinationDirectory, fileNameWithExtension)
        return destinationFile.readText()
    }

    /**
     * Clears the project directory and creates the build files.
     */
    private fun initializeProjectFiles(processors: List<String>, plugins: List<String> = emptyList()) {
        // Set up the test build
        projectDirectory.deleteRecursively()
        projectDirectory.mkdirs()

        File(projectDirectory, "settings.gradle.kts")
            .write(settingsFile)

        File(projectDirectory, "build.gradle.kts")
            .write(getBuildFileContent(processors, plugins))
    }

    /**
     * Writes the [additionalFiles] to the project directory.
     */
    private fun writeAdditionalFiles(additionalFiles: List<Additional>) {
        for (additional in additionalFiles) {
            with(File(projectDirectory, additional.relativePath)) {
                when (additional) {
                    is AdditionalDirectory -> mkdirs()
                    is AdditionalFile -> write(additional.content)
                }
            }
        }
    }

    /**
     * Returns the relative path for the given [packageName] and [language].
     */
    private fun getRelativePath(
        language: FileLanguage,
        packageName: String
    ): String = buildString {
        append("src/main/")
        append(
            when (language) {
                FileLanguage.JAVA -> "java/"
                FileLanguage.KOTLIN -> "kotlin/"
            }
        )
        append(packageName.replace('.', '/'))
    }

    /**
     * Returns the file name with the correct extension for the given [fileName] and [language].
     */
    private fun getFileNameWithExtension(fileName: String, language: FileLanguage): String {
        val extension = when (language) {
            FileLanguage.JAVA -> "java"
            FileLanguage.KOTLIN -> "kt"
        }
        return "$fileName.$extension"
    }

    /**
     * Runs the build and returns the result.
     */
    private fun runBuild(): BuildResult =
        GradleRunner.create()
            .forwardOutput()
            .withArguments("processKdocMain")
            .withProjectDir(projectDirectory)
            .withDebug(true)
            .build()

    /**
     * Writes the given [string] to this file.
     */
    @Throws(IOException::class, SecurityException::class)
    protected fun File.write(string: String) {
        if (!parentFile.exists()) {
            val result = parentFile.mkdirs()
            if (!result) {
                throw IOException("Could not create parent directories for $this")
            }
        }

        FileWriter(this).use { writer -> writer.write(string) }
    }
}