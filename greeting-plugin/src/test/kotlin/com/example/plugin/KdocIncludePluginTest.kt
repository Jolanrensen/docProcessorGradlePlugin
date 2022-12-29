package com.example.plugin

import io.kotest.matchers.shouldBe
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Test

class KdocIncludePluginTest {
    @Test
    fun pluginRegistersATask() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.example.plugin.kdocInclude")

        // Verify the result
        Assert.assertNotNull(project.tasks.findByName("processKdocInclude"))
    }

    @Test
    fun `Test Kdoc utils`() {
        val kdoc1 = """
            /**
             * Hello World!
             * 
             * @see [com.example.plugin.KdocIncludePlugin]
             */
        """.trimIndent()

        kdoc1.getKdocContent().toKdoc() shouldBe kdoc1

        val kdoc2 = """
            /** Hello World!
             * 
             * @see [com.example.plugin.KdocIncludePlugin] */
        """.trimIndent()

        kdoc2.getKdocContent().toKdoc() shouldBe kdoc2

        val kdoc3 = """
            /** Hello World!
             * @see [com.example.plugin.KdocIncludePlugin] */
        """.trimIndent()

        kdoc3.getKdocContent().toKdoc() shouldBe kdoc3

        val kdoc4 = """
            /** Hello World! */
        """.trimIndent()

        kdoc4.getKdocContent().toKdoc() shouldBe kdoc4

        val kdoc5 = """
            /**
             * Hello World!
             */
        """.trimIndent()

        kdoc5.getKdocContent().toKdoc() shouldBe kdoc5
    }
}