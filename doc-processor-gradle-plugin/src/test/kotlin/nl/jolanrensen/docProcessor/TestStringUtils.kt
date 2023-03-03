package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
import org.junit.Test

class TestStringUtils {

    @Test
    fun `Last index of not`() {
        val string = "Hello World!"

        string.lastIndexOfNot('a') shouldBe 0
        string.lastIndexOfNot('H') shouldBe 1
        string.lastIndexOfNot('l') shouldBe 10
        string.lastIndexOfNot('l', 5) shouldBe 4
    }

    @Test
    fun `Replace ranges`() {
        val someText = """
            Hello World!
            This is some text.
            It has many lines.
            I hope you like it.
        """.trimIndent()

        val replacements = mapOf(
            0..4 to "Hi",
            6..11 to "World",
            26..29 to "other text then before",
        )

        someText.replaceRanges(replacements) shouldBe
                """
                    Hi World
                    This is some other text then before.
                    It has many lines.
                    I hope you like it.""".trimIndent()
    }

    @Test
    fun `Replace all link regexes`() {
        val someText = """
            [Hello] [World]!
            This is [text][text].
            It has many lines.
            I hope you [like][it] [right?]""".trimIndent()

        val res = someText.replaceKdocLinks { "NewPath" }

        res shouldBe """
            [Hello][NewPath] [World][NewPath]!
            This is [text][NewPath].
            It has many lines.
            I hope you [like][NewPath] [right?][NewPath]""".trimIndent()
    }

}