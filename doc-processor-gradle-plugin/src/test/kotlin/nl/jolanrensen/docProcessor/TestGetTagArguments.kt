package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
import org.junit.Test

class TestGetTagArguments {

    @Test
    fun `Simple content with spaces`() {
        val tagContent = "{@tag simple content with spaces}"

        tagContent.getTagArguments("tag", 1) shouldBe listOf("simple content with spaces")
        tagContent.getTagArguments("tag", 2) shouldBe listOf("simple", "content with spaces")
        tagContent.getTagArguments("tag", 3) shouldBe listOf("simple", "content", "with spaces")
    }

    @Test
    fun `Some more difficult content with spaces`() {
        val tagContent = "@tag [some (\"more \\]`difficult] (content with) spaces"

        tagContent
            .getTagArguments("tag", 1)
            .map { it.removeEscapeCharacters() } shouldBe
                listOf(
                    "[some (\"more ]`difficult] (content with) spaces",
                )

        tagContent
            .getTagArguments("tag", 2)
            .map { it.removeEscapeCharacters() } shouldBe
                listOf(
                    "[some (\"more ]`difficult]",
                    "(content with) spaces",
                )

        tagContent
            .getTagArguments("tag", 3)
            .map { it.removeEscapeCharacters() } shouldBe
                listOf(
                    "[some (\"more ]`difficult]",
                    "(content with)",
                    "spaces",
                )
    }

    @Test
    fun `Content with newlines`() {
        val tagContent = "@tag simple content with newlines\n\n"

        tagContent.getTagArguments("tag", 1) shouldBe listOf("simple content with newlines\n\n")
        tagContent.getTagArguments("tag", 2) shouldBe listOf("simple", "content with newlines\n\n")
        tagContent.getTagArguments("tag", 3) shouldBe listOf("simple", "content", "with newlines\n\n")
    }

    @Test
    fun `Content with newlines and spaces`() {
        val tagContent = "@tag simple  content\nwith\n  newlines   \n \n"

        tagContent.getTagArguments("tag", 1) shouldBe listOf("simple  content\nwith\n  newlines   \n \n")
        tagContent.getTagArguments("tag", 2) shouldBe listOf("simple", " content\nwith\n  newlines   \n \n")
        tagContent.getTagArguments("tag", 3) shouldBe listOf("simple", "content", "\nwith\n  newlines   \n \n")
        tagContent.getTagArguments("tag", 4) shouldBe listOf("simple", "content", "with", "\n  newlines   \n \n")
        tagContent.getTagArguments("tag", 5) shouldBe listOf("simple", "content", "with", "newlines", "  \n \n")
    }

    @Test
    fun `Newline right after first argument inline`() {
        val tagContent = "@tag {@link com.example.plugin.JavaMain.Main2.TestB}\n"

        tagContent.getTagArguments("tag", 1) shouldBe listOf("{@link com.example.plugin.JavaMain.Main2.TestB}\n")
        tagContent.getTagArguments("tag", 2) shouldBe listOf("{@link com.example.plugin.JavaMain.Main2.TestB}", "\n")
    }

    @Test
    fun `Newline right after first argument block`() {
        val tagContent = "@tag [Test]\nHello"

        tagContent.getTagArguments("tag", 1) shouldBe listOf("[Test]\nHello")
        tagContent.getTagArguments("tag", 2) shouldBe listOf("[Test]", "\nHello")
    }

    @Test
    fun `Java link`() {
        val tagContent = "@include {@link com.example.plugin.JavaMain.Main2.TestB}"

        tagContent.getTagArguments("include", 1) shouldBe
                listOf("{@link com.example.plugin.JavaMain.Main2.TestB}")

        tagContent.getTagArguments("include", 1)
            .first()
            .decodeCallableTarget() shouldBe "com.example.plugin.JavaMain.Main2.TestB"
    }

    @Test
    fun `Kotlin link`() {
        val tagContent = "@tag [Something] with a newline at the end\n"

        tagContent.getTagArguments("tag", 1) shouldBe
                listOf(
                    "[Something] with a newline at the end\n",
                )

        tagContent.getTagArguments("tag", 2) shouldBe
                listOf(
                    "[Something]",
                    "with a newline at the end\n",
                )

        tagContent.getTagArguments("tag", 2)
            .first()
            .decodeCallableTarget() shouldBe "Something"
    }
}