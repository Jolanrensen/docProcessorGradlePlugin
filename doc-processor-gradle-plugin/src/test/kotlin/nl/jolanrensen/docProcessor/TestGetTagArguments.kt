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
        val tagContent = "@arg [Something] with a newline at the end\n"

        tagContent.getTagArguments("arg", 1) shouldBe
                listOf(
                    "[Something] with a newline at the end",
                )

        tagContent.getTagArguments("arg", 2) shouldBe
                listOf(
                    "[Something]",
                    "with a newline at the end",
                )

        tagContent.getTagArguments("arg", 2)
            .first()
            .decodeCallableTarget() shouldBe "Something"
    }
}