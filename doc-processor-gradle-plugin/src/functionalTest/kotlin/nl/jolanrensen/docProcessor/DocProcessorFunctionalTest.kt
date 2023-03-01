@file:Suppress("LeakingThis")

package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
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
        import nl.jolanrensen.docProcessor.*
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

    val projectDir = File("build/$name")
    val outputDir = File(projectDir, "build/docProcessor/processKdocMain")

    private fun initializeProjectFiles(processors: List<String>) {
        // Set up the test build
        projectDir.deleteRecursively()
        projectDir.mkdirs()

        File(projectDir, "settings.gradle.kts")
            .writeString(settingsFile)

        File(projectDir, "build.gradle.kts")
            .writeString(getBuildFileContent(processors))
    }

    private fun runBuild(): BuildResult =
        GradleRunner.create()
            .forwardOutput()
            .withArguments("processKdocMain")
            .withProjectDir(projectDir)
            .withDebug(true)
            .build()

    enum class JavaOrKotlin {
        JAVA, KOTLIN
    }

    fun createUniqueFileName(
        dir: File,
        javaOrKotlin: JavaOrKotlin,
    ): String {
        val extension = when (javaOrKotlin) {
            JavaOrKotlin.JAVA -> "java"
            JavaOrKotlin.KOTLIN -> "kt"
        }

        var i = 0
        while (true) {
            val fileName = "Test$i.$extension"
            if (!File(dir, fileName).exists()) return fileName
            i++
        }
    }

    fun testContentSingleFile(
        content: String,
        expectedOutput: String,
        javaOrKotlin: JavaOrKotlin = JavaOrKotlin.KOTLIN,
        packageName: String,
        fileName: String = "Test",
        processors: List<String>,
    ) {
        initializeProjectFiles(processors)

        val relativePath = buildString {
            append("src/main/")
            append(
                when (javaOrKotlin) {
                    JavaOrKotlin.JAVA -> "java/"
                    JavaOrKotlin.KOTLIN -> "kotlin/"
                }
            )
            append(packageName.replace('.', '/'))
        }
        val dir = File(projectDir, relativePath)

        val extension = when (javaOrKotlin) {
            JavaOrKotlin.JAVA -> "java"
            JavaOrKotlin.KOTLIN -> "kt"
        }
        val fileNameWithExtension = "$fileName.$extension"

        File(dir, fileNameWithExtension).writeString(content)

        runBuild()

        val result = File(File(outputDir, relativePath), fileNameWithExtension).readText()

        result shouldBe expectedOutput
    }

    @Throws(IOException::class)
    private fun File.writeString(string: String) {
        if (!parentFile.exists()) {
            parentFile.mkdirs()
        }

        FileWriter(this).use { writer -> writer.write(string) }
    }
}