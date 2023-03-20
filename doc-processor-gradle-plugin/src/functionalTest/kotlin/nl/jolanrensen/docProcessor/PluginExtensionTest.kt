@file:Suppress("FunctionName")

package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import org.junit.Test
import java.io.File

class PluginExtensionTest : DocProcessorFunctionalTest("extension") {

    private val processors = listOf(
        "\"nl.jolanrensen.extension.Extension\"",
    )

    private val plugins = listOf(
        "nl.jolanrensen:PluginExtensionTest:$version",
    )

    @Language("kts")
    private val settingsFile = """
        rootProject.name = "PluginExtensionTest"
    """.trimIndent()

    @Language("kts")
    private val buildFile: String = """
        plugins {  
            kotlin("jvm") version "1.8.10"
            id("com.vanniktech.maven.publish") version "0.20.0"
        }

        group = "nl.jolanrensen"
        version = "$version"
        
        repositories {
            mavenLocal()
            gradlePluginPortal()
            mavenCentral()
        }
        
        dependencies {
            compileOnly("nl.jolanrensen.docProcessor:doc-processor-gradle-plugin:$version")
        }
    """.trimIndent()

    @Language("kt")
    private val extensionPlugin = """
        package nl.jolanrensen.extension

        import nl.jolanrensen.docProcessor.DocProcessor
        import nl.jolanrensen.docProcessor.DocumentableWrapper
        
        class Extension : DocProcessor() {
        
            override fun process(
                processLimit: Int,
                documentablesByPath: Map<String, List<DocumentableWrapper>>,
            ): Map<String, List<DocumentableWrapper>> =
                documentablesByPath.mapValues { (_, v) ->
                    v.map {
                        it.copy(
                            docContent = it.docContent
                                .replace('e', 'a')
                                .replace('E', 'A'),
                            isModified = true,
                        )
                    }
                }
        }
    """.trimIndent()

    private val projectDirectory = File("build/extension-plugin")

    init {
        initializePluginProjectFiles()
        writeAdditionalPluginFiles(
            listOf(
                AdditionalFile(
                    relativePath = "settings.gradle.kts",
                    content = settingsFile,
                ),
                AdditionalFile(
                    relativePath = "build.gradle.kts",
                    content = buildFile,
                ),
                AdditionalFile(
                    relativePath = "src/main/kotlin/nl/jolanrensen/extension/Extension.kt",
                    content = extensionPlugin,
                ),
                AdditionalFile(
                    relativePath = "src/main/resources/META-INF/services/nl.jolanrensen.docProcessor.DocProcessor",
                    content = "nl.jolanrensen.extension.Extension",
                ),
            )
        )

        GradleRunner.create()
            .forwardOutput()
            .withArguments("publishToMavenLocal")
            .withProjectDir(projectDirectory)
            .withDebug(true)
            .build()
    }

    /**
     * Clears the project directory and creates the build files.
     */
    private fun initializePluginProjectFiles() {
        // Set up the test build
        projectDirectory.deleteRecursively()
        projectDirectory.mkdirs()

        File(projectDirectory, "settings.gradle.kts")
            .write(settingsFile)

        File(projectDirectory, "build.gradle.kts")
            .write(buildFile)
    }

    /**
     * Writes the [additionalFiles] to the project directory.
     */
    private fun writeAdditionalPluginFiles(additionalFiles: List<Additional>) {
        for (additional in additionalFiles) {
            with(File(projectDirectory, additional.relativePath)) {
                when (additional) {
                    is AdditionalDirectory -> mkdirs()
                    is AdditionalFile -> write(additional.content)
                }
            }
        }
    }

    @Test
    fun `E to A processor`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * eeeEEEE
             */
            class TestClass {
            
                /**
                 * This is a test function.
                 */
                fun testFunction() {
                    println("Hello World!")
                }
            
            }
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * aaaAAAA
             */
            class TestClass {
            
                /**
                 * This is a tast function.
                 */
                fun testFunction() {
                    println("Hello World!")
                }
            
            }
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            plugins = plugins,
        ) shouldBe expectedOutput
    }

}