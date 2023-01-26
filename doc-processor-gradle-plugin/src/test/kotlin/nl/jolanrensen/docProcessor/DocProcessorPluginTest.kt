package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

class DocProcessorPluginTest {
    @Test
    fun pluginRegistersATask() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("nl.jolanrensen.docProcessor")
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
    private val difficultKdoc =
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

        difficultKdoc.splitDocContent() shouldBe expected
        difficultKdoc.splitDocContent().joinToString("\n") shouldBe difficultKdoc
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

        difficultKdoc.splitDocContentOnInnerTags() shouldBe expected
        difficultKdoc.splitDocContentOnInnerTags().joinToString("") shouldBe difficultKdoc
    }

    @Test
    fun `Test Kdoc utils`() {
        val kdoc1 = """
            /**
             *       Hello World!
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

        // this is not a pretty kdoc, but we can still parse it and correct the indentation
        val kdoc6 = """
            /**
             *Hello World!
             */
        """.trimIndent()

        val kdoc6a = """
            /**
             * Hello World!
             */
        """.trimIndent()


        kdoc6.getDocContent().toDoc() shouldBe kdoc6a
    }
}


