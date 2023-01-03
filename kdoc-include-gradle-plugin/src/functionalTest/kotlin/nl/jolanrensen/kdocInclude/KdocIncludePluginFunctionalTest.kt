package nl.jolanrensen.kdocInclude

import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.FileWriter
import java.io.IOException

class KdocIncludePluginFunctionalTest {

    @Language("kts")
    private val buildFile = """
        plugins {  
            kotlin("jvm") version "1.8.0"
            id("nl.jolanrensen.kdocInclude")
        }
        
        val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
        
        val processKdocIncludeMain by tasks.creating(nl.jolanrensen.kdocInclude.ProcessKdocIncludeTask::class) {
            sources.set(kotlinMainSources)
            fileExtensions.set(listOf("kt", "java", "scala"))
        }
        
        tasks.compileKotlin { dependsOn(processKdocIncludeMain) }
    """.trimIndent()

    @Language("kt")
    private val kotlinFile = """
        package com.example.plugin

        /**
         * Hello World!
         * 
         * This is a large example of how the plugin will work
         * 
         * @param name The name of the person to greet
         * @see [com.example.plugin.KdocIncludePlugin]
         */

        private interface TestA
        
        /**
         * Hello World 2!
         * @include [TestA]
         */
        @AnnotationTest(a = 24)
        private interface Test

        /** 
         * Some extra text
         * @include [Test] */
        fun someFun() {
            println("Hello World!")
        }

        /** @include [com.example.plugin.Test] */
        fun someMoreFun() {
            println("Hello World!")
        }
    """.trimIndent()

    @Language("java")
    private val javaFile = """
        package com.example.plugin;
        
        class Main {

            /**
             * Hello World!
             * <p> 
             * This is a large example of how the plugin will work
             * 
             * @param name The name of the person to greet
             * @see com.example.plugin.KdocIncludePlugin
             */
    
            private interface TestA {}
            
            /**
             * Hello World 2!
             * @include TestA
             */
            @AnnotationTest(a = 24)
            private interface Test {}
    
            /** 
             * Some extra text
             * @include Test */
            void someFun() {
                System.out.println("Hello World!");
            }
    
            /** @include <code>com.example.plugin.Test</code> */
            void someMoreFun() {
                System.out.println("Hello World!");
            }
        }
    """.trimIndent()

    @Language("scala")
    private val scalaFile = """
        package com.example.plugin

        /**
         * Hello World!
         * 
         * This is a large example of how the plugin will work
         * 
         * @param name The name of the person to greet
         * @see [[com.example.plugin.KdocIncludePlugin]]
         */

        private trait TestA
        
        /**
         * Hello World 2!
         *
         * @include [[TestA]]
         */
        @AnnotationTest(a = 24)
        private trait Test

        /** 
         * Some extra text
         *
         * @include [[Test]] */
        def someFun(): Unit = {
            println("Hello World!")
        }

        /** @include [[com.example.plugin.Test]] */
        def someMoreFun(): Unit = {
            println("Hello World!")
        }
    """.trimIndent()

    @Test
    @Throws(IOException::class)
    fun canRunTask() {
        // Set up the test build
        val projectDir = File("build/functionalTest")
        projectDir.mkdirs()

        File(projectDir, "settings.gradle.kts")
            .writeString("")

        File(projectDir, "build.gradle.kts")
            .writeString(buildFile)

        File(projectDir, "src/main/java/com/example/plugin/Main.kt")
            .writeString(kotlinFile)

        File(projectDir, "src/main/java/com/example/plugin/Main.java")
            .writeString(javaFile)

        File(projectDir, "src/main/java/com/example/plugin/Main.scala")
            .writeString(scalaFile)

        // Run the build
        val result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("processKdocIncludeMain")
            .withProjectDir(projectDir)
            .build()

        // Verify the result
        Assert.assertTrue(result.output.contains("Hello from plugin 'nl.jolanrensen.kdocInclude'"))
    }

    @Throws(IOException::class)
    private fun File.writeString(string: String) {
        if (!parentFile.exists()) {
            parentFile.mkdirs()
        }

        FileWriter(this).use { writer -> writer.write(string) }
    }
}