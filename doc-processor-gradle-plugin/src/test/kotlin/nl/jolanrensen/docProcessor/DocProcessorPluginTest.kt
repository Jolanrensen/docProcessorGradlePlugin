package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Test

class DocProcessorPluginTest {
    @Test
    fun pluginRegistersATask() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("nl.jolanrensen.docProcessor")

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

        kdoc1.getDocContent().toDoc() shouldBe kdoc1

        val kdoc2 = """
            /** Hello World!
             * 
             * @see [com.example.plugin.KdocIncludePlugin] */
        """.trimIndent()

        kdoc2.getDocContent().toDoc() shouldBe kdoc2

        val kdoc3 = """
            /** Hello World!
             * @see [com.example.plugin.KdocIncludePlugin] */
        """.trimIndent()

        kdoc3.getDocContent().toDoc() shouldBe kdoc3

        val kdoc4 = """
            /** Hello World! */
        """.trimIndent()

        kdoc4.getDocContent().toDoc() shouldBe kdoc4

        val kdoc5 = """
            /**
             * Hello World!
             */
        """.trimIndent()

        kdoc5.getDocContent().toDoc() shouldBe kdoc5
    }
}


