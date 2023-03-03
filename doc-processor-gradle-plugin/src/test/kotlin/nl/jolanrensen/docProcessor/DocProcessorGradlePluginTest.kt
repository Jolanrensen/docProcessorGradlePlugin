package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

class DocProcessorGradlePluginTest {
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
     * Other test {@c [TestA
     * blabla {@d TestA }
     * }
     * Other test `{@h TestA}`
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
           |Other test {@c [TestA
           |blabla {@d TestA }
           |}
           |Other test `{@h TestA}`
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
               |Other test {@c [TestA
               |blabla {@d TestA }
               |}
               |Other test `{@h TestA}`
               |```kotlin
               |@e TestB
               |{@f TestC }
               |```""",

            """|@g Test""",
        ).map { it.trimMargin() }

        difficultKdoc.splitDocContentPerBlock() shouldBe expected
        difficultKdoc.splitDocContentPerBlock().joinToString("\n") shouldBe difficultKdoc
    }

    @Test
    fun `Test find inline tags in Doc Content()`() {
        val expected = setOf(
            "c", "d", "h", "f"
        )

        difficultKdoc.findInlineTagNamesInDocContent().toSet() shouldBe expected
    }


    @Test
    fun `Get tag content`() {
        val tagContent1 = "{@tag simple content with spaces}"

        tagContent1.getTagArguments("tag", 1) shouldBe listOf("simple content with spaces")
        tagContent1.getTagArguments("tag", 2) shouldBe listOf("simple", "content with spaces")
        tagContent1.getTagArguments("tag", 3) shouldBe listOf("simple", "content", "with spaces")

        val tagContent2 = "@tag [some (\"more `difficult] (content with) spaces"

        tagContent2.getTagArguments("tag", 1) shouldBe listOf("[some (\"more `difficult] (content with) spaces")
        tagContent2.getTagArguments("tag", 2) shouldBe listOf("[some (\"more `difficult]", "(content with) spaces")
        tagContent2.getTagArguments("tag", 3) shouldBe listOf("[some (\"more `difficult]", "(content with)", "spaces")

        val tagContent3 = "@include {@link com.example.plugin.JavaMain.Main2.TestB}"

        tagContent3.getTagArguments("include", 1) shouldBe listOf("{@link com.example.plugin.JavaMain.Main2.TestB}")
        tagContent3.getTagArguments("include", 1)
            .first()
            .decodeCallableTarget() shouldBe "com.example.plugin.JavaMain.Main2.TestB"

        val tagContent4 = "@arg [Something] with a newline at the end\n"
        tagContent4.getTagArguments("arg", 1) shouldBe listOf("[Something] with a newline at the end")
        tagContent4.getTagArguments("arg", 2) shouldBe listOf("[Something]", "with a newline at the end")

    }

}


