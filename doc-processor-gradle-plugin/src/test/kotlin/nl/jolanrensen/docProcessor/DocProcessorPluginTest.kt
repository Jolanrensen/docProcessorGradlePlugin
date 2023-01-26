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
//        Assert.assertNotNull(project.tasks.findByName("processKdocInclude"))
    }

    /**
     * blablah
     *   @a a
     * Some extra text. @b nothing, this is skipped
     * Other test {@c TestA
     * blabla {@d TestA }
     * }
     * Other test `{@d TestA}`
     * ```kotlin
     * @e TestB
     * {@f TestC }
     * ```
     * @g Test
     */
    private val text =
        """|blablah
           |  @a a
           |Some extra text. @b nothing, this is skipped
           |Other test {@c TestA
           |blabla {@d TestA }
           |}
           |Other test `{@d TestA}`
           |```kotlin
           |@e TestB
           |{@f TestC }
           |```
           |@g Test""".trimMargin()

    @Test
    fun `Test split Doc Content`() {
        val expected = listOf(
            """|blablah""",

            """|  @a a
               |Some extra text. @b nothing, this is skipped
               |Other test {@c TestA
               |blabla {@d TestA }
               |}
               |Other test `{@d TestA}`
               |```kotlin
               |@e TestB
               |{@f TestC }
               |```""",

            """|@g Test""",
        ).map { it.trimMargin() }

        text.splitDocContent() shouldBe expected
        text.splitDocContent().joinToString("\n") shouldBe text
    }

    @Test
    fun `Test split doc content on inner tags()`() {
        val expected = listOf(
            """|blablah
               |  @a a
               |Some extra text. @b nothing, this is skipped
               |Other test """,

            """|{@c TestA
               |blabla {@d TestA }
               |}""", // not gonna split inner inner tags

            """|
               |Other test `{@d TestA}`
               |```kotlin
               |@e TestB
               |{@f TestC }
               |```
               |@g Test"""
        ).map { it.trimMargin() }

        text.splitDocContentOnInnerTags() shouldBe expected
        text.splitDocContentOnInnerTags().joinToString("") shouldBe text
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


