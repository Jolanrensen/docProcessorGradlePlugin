package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

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
            "\n\nblablah",

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

            "@g Test\n",
        )

        difficultKdoc.splitDocContentPerBlock() shouldBe expected
        difficultKdoc.splitDocContentPerBlock().joinToString("\n") shouldBe difficultKdoc
    }

    @Test
    fun `Find inline tag names with ranges`() {
        val expected = listOf(
            "d" to 92..102,
            "c" to 74..104,
            "h" to 118..127,
            "f" to 149..159,
        )

        difficultKdoc.findInlineTagNamesInDocContentWithRanges() shouldBe expected
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

    @Test
    fun `Split by block simple`() {
        val kdoc = """
            /**
             * Hello World! 
             * [Some aliased link][helloWorld2] 
             * [helloWorld]
             */
        """.trimIndent()

        kdoc.getDocContentOrNull()
            ?.splitDocContentPerBlock()
            ?.joinToString("\n")
            ?.toDoc() shouldBe kdoc
    }

    @Test
    fun `Split by block one line`() {
        val kdoc = """
            /** Hello World! */
        """.trimIndent()

        kdoc.getDocContentOrNull()
            ?.splitDocContentPerBlock()
            ?.joinToString("\n")
            ?.toDoc() shouldBe kdoc
    }

    @Test
    fun `Split by block one line with tag`() {
        val kdoc = """
            /** @include Hello World! */
        """.trimIndent()

        kdoc.getDocContentOrNull()
            ?.splitDocContentPerBlock()
            ?.joinToString("\n")
            ?.toDoc() shouldBe kdoc
    }
}