package nl.jolanrensen.docProcessor

import org.gradle.testkit.runner.GradleRunner
import org.junit.Ignore
import org.junit.Test
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.exists

class DataFrameTest : DocProcessorFunctionalTest("df") {

    private val projectDirectory = File("src/functionalTest/resources/dataframe")
    private val outputDirectory = File(projectDirectory, "core/build/generated-sources")

    init {
        require(projectDirectory.exists()) {
            "The DataFrame directory does not exist. Please git clone the repository in the resources folder."
        }
        updateVersionInFiles()
    }

    private fun updateVersionInFiles() {
        val tomlFile = projectDirectory.resolve("gradle/libs.versions.toml")
        var txt = tomlFile.readText()
        txt = txt.replace("docProcessor = \"[^\"]+\"".toRegex(), "docProcessor = \"$version\"")
        tomlFile.write(txt)
    }

    @Test
//    @Ignore
    fun `build DataFrame`() {
        GradleRunner.create()
            .forwardOutput()
            .withArguments("clean", "core:processKDocsMain")
            .withProjectDir(projectDirectory)
            .withDebug(true)
            .build()
    }
}