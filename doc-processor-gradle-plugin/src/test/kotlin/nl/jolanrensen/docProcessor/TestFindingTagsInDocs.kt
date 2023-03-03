package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
import org.junit.Test

class TestFindingTagsInDocs {

    @Test
    fun `Find tag name block`() {
        "  @someTag someContent".getTagNameOrNull() shouldBe "someTag"
    }

    @Test
    fun `Find tag name inline`() {
        "{@someTag someContent}".getTagNameOrNull() shouldBe "someTag"
    }

    private val difficultKdoc = """
        blablah
          @a a
        Some extra text. @b nothing, this is skipped
        Other test {@c [TestA
        blabla {@d TestA }
        }
        Other test `{@h TestA}`
        ```kotlin
        @e TestB
        {@f TestC }
        ```
        @g Test
        """.trimIndent()

    @Test
    fun `Split doc content per block`() {
        val expected = listOf(
            "blablah",

            """
                 @a a
               Some extra text. @b nothing, this is skipped
               Other test {@c [TestA
               blabla {@d TestA }
               }
               Other test `{@h TestA}`
               ```kotlin
               @e TestB
               {@f TestC }
               ```
               """.trimIndent(),

            "@g Test",
        )

        difficultKdoc.splitDocContentPerBlock() shouldBe expected
        difficultKdoc.splitDocContentPerBlock().joinToString("\n") shouldBe difficultKdoc
    }

    @Test
    fun `Find inline tags in doc content`() {
        val expected = setOf(
            "c", "d", "h", "f"
        )

        difficultKdoc.findInlineTagNamesInDocContent().toSet() shouldBe expected
    }

    @Test
    fun `Find block tags in doc content`() {
        val expected = setOf(
            "a", "g"
        )

        difficultKdoc.findBlockTagNamesInDocContent().toSet() shouldBe expected
    }
}