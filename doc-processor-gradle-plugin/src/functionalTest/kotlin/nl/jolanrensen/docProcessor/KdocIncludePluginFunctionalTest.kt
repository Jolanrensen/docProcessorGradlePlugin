package nl.jolanrensen.docProcessor

import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.FileWriter
import java.io.IOException

@Deprecated("")
class KdocIncludePluginFunctionalTest {

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
    private val buildFile = """
        import nl.jolanrensen.docProcessor.*
        import nl.jolanrensen.docProcessor.defaultProcessors.*

        plugins {  
            kotlin("jvm") version "1.8.0"
            id("nl.jolanrensen.docProcessor") version "1.0-SNAPSHOT"
        }
        
        val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
        
        val processKdocIncludeMain by creatingProcessDocTask(sources = kotlinMainSources) {
            debug = true
            processors += INCLUDE_DOC_PROCESSOR
            processors += INCLUDE_FILE_DOC_PROCESSOR
            processors += INCLUDE_ARG_DOC_PROCESSOR
            processors += COMMENT_DOC_PROCESSOR
            processors += SAMPLE_DOC_PROCESSOR
//            processors += TODO_DOC_PROCESSOR
        }
        
        tasks.compileKotlin { dependsOn(processKdocIncludeMain) }
    """.trimIndent()

    @Language("kt")
    private val kotlinFile = """
        package com.example.plugin

        /**
         * Hello World!
         * 
         * This is a large example of how the plugin will work from {@includeArg [TestA]}!
         * 
         * @includeArg [TestA] with extra text
         * @param name The name of the person to greet
         * @see [Hi][TestB]
         * @see [TestA]
         * @see [TestB]
         * {@comment If you provide a default value for an argument, make sure to make it inline, so it can be 
         * overwritten by other inline tags, since block tags are processed after inline tags and would thus overwrite it.}
         * {@arg [TestA] <DEFAULT>}
         */

        private interface TestA

        /** Single line */
        private interface TestB
        
        /**
         * Hello World 2! [someFun] [String.someFun]
         * @include [TestA] {@arg [Some Alias][com.example.plugin.TestA] Test}
         */
        @AnnotationTest(a = 24)
        private interface Test

        /** 
         * Some extra text @include nothing, this is skipped
         * Other test {@include [TestA]} 
         * 
         * @include [Test] aaa
         * @arg [TestA] "someFun" with {@include [TestB]}
         */
        fun <T> List<T?>.someFunn(a: Int) {
            println("Hello World!")
        }

        /** @include [com.example.plugin.TestB] */
        fun String.someFun(b: String) {
            println("Hello World!")
        }

        /**
         * Some constant
         * {@includeArg [kotlin.String.someFun]} 
         * {@arg [com.example.plugin.someFun] test} 
         * @sample [kotlin.String.someFun]
         */
        const val someLanguages = "Kotlin"

        /**
         * Some constant
         * @sampleNoComments [JavaMain]
         * ```kts
         * {@includeFile (../../../../../../build.gradle.kts)}
         * ```
         * 
         */
        const val someOtherLanguages = "Kotlin"
    """.trimIndent()

    @Language("kotlin")
    private val secondKotlinFile = """
        /**
         * @include [someFun2]
         */
        fun someSample() {
            println("Hello World!")
        }

        /**
         * Hello
         */
        fun someFun2() {
            println("Hello World!")
        }

        /**
         * First sample:
         * @sample [someSample]
         *
         * Second sample: nothing
         *
         * Final sample:
         * @sample someFun2
         */
        fun someFun() {
        
        }
    """.trimIndent()

    @Language("java")
    private val javaFile = """
        package com.example.plugin;
        
        class JavaMain {
            class Main2 {

                /**
                 * Hello World!
                 * <p> 
                 * This is a large example of how the plugin will work
                 * 
                 * @param name The name of the person to greet
                 * @see com.example.plugin.KdocIncludePlugin
                 */
        
                private interface TestA {}

                /** Single line */
                private interface TestB {}
                
                /**
                 * Hello World 2!
                 * @include TestA
                 */
                @AnnotationTest(a = 24)
                private interface Test {}
        
                /** 
                 * Some extra text. @include nothing, this is skipped
                 * Other test {@include TestA} 
                 * @include Test */
                @AnnotationTest(a = 24)
                <T> void someFun(int a) {
                    System.out.println("Hello World!");
                }
        
                /** @include {@link com.example.plugin.JavaMain.Main2.TestB} */
                void someFun(String b) {
                    System.out.println("Hello World!");
                }
                
                /**
                 * Some constant
                 * @sample someFun
                 */
                final String someLanguages = "Kotlin";
                
                /**
                 * Some other constant
                 * @sampleNoComments Main2
                 * @includeFile (../../../../../../build.gradle.kts) 
                 */
                final String someOtherLanguages = "Kotlin";
            }
        }
    """.trimIndent()

    private fun setup(): File {
        // Set up the test build
        val projectDir = File("build/functionalTest")
        projectDir.mkdirs()

        File(projectDir, "settings.gradle.kts")
            .writeString(settingsFile)

        println("NOTE!! make sure you have the plugin installed in your local maven repo")
        File(projectDir, "build.gradle.kts")
            .writeString(buildFile)

        File(projectDir, "src/main/kotlin/com/example/plugin/KotlinMain.kt")
            .writeString(kotlinFile)

        File(projectDir, "src/main/kotlin/SecondKotlinMain.kt")
            .writeString(secondKotlinFile)

        File(projectDir, "src/main/java/com/example/plugin/JavaMain.java")
            .writeString(javaFile)

        return projectDir
    }

//    @Test
    @Throws(IOException::class)
    fun canRunTaskWithPluginClasspath() {
        val projectDir = setup()

        // Run the build
        val result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("processKdocIncludeMain")
            .withProjectDir(projectDir)
            .withDebug(true)
            .build()

        // Verify the result
        Assert.assertTrue(result.output.contains("Hello from plugin 'nl.jolanrensen.docProcessor'"))
    }

    @Test
    @Throws(IOException::class)
    fun canRunTaskWithoutPluginClasspath() {
        val projectDir = setup()

        // Run the build
        val result = GradleRunner.create()
            .forwardOutput()
            .withArguments("processKdocIncludeMain")
            .withProjectDir(projectDir)
            .withDebug(true)
            .build()

        // Verify the result
        Assert.assertTrue(result.output.contains("Hello from plugin 'nl.jolanrensen.docProcessor'"))
    }

    @Throws(IOException::class)
    private fun File.writeString(string: String) {
        if (!parentFile.exists()) {
            parentFile.mkdirs()
        }

        FileWriter(this).use { writer -> writer.write(string) }
    }
}