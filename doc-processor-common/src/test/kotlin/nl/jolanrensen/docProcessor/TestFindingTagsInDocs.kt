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
    fun `Find tag after difficult doc content`() {
        val expectedInline = setOf("includeArg")
        val expectedBlock = setOf("param", "return", "arg", "see")
        val kdoc1 = """
            ## Cols
            Creates a subset of columns ([ColumnSet][org.jetbrains.kotlinx.dataframe.columns.ColumnSet]) from a parent [ColumnSet][org.jetbrains.kotlinx.dataframe.columns.ColumnSet], -[ColumnGroup][org.jetbrains.kotlinx.dataframe.columns.ColumnGroup], or -[DataFrame][org.jetbrains.kotlinx.dataframe.DataFrame].
            You can use either a [ColumnFilter][org.jetbrains.kotlinx.dataframe.ColumnFilter] or any of the `vararg` overloads for all
            [APIs][org.jetbrains.kotlinx.dataframe.documentation.AccessApi] (+ [ColumnPath][org.jetbrains.kotlinx.dataframe.columns.ColumnPath]).
            
            Aside from calling [cols][org.jetbrains.kotlinx.dataframe.api.ColumnsSelectionDsl.cols] directly, you can also use the [get][org.jetbrains.kotlinx.dataframe.api.ColumnsSelectionDsl.get] operator in most cases.
            
            #### For example:
            `df.`[remove][org.jetbrains.kotlinx.dataframe.DataFrame.remove]` { `[cols][org.jetbrains.kotlinx.dataframe.api.ColumnsSelectionDsl.cols]` { it.`[hasNulls][org.jetbrains.kotlinx.dataframe.hasNulls]`() } }`
            
            `df.`[select][org.jetbrains.kotlinx.dataframe.DataFrame.select]` { myGroupCol.`[cols][org.jetbrains.kotlinx.dataframe.api.ColumnsSelectionDsl.cols]`(columnA, columnB) }`
            
            `df.`[select][org.jetbrains.kotlinx.dataframe.DataFrame.select]` { `[colsOf][org.jetbrains.kotlinx.dataframe.api.ColumnsSelectionDsl.colsOf]`<`[String][String]`>()`[`[`][org.jetbrains.kotlinx.dataframe.api.ColumnsSelectionDsl.cols]`1, 3, 5`[`]`][org.jetbrains.kotlinx.dataframe.api.ColumnsSelectionDsl.cols]` }`
            
            
            #### Examples for this overload:
            
            {@getArg [CommonColsDocs.Examples][org.jetbrains.kotlinx.dataframe.api.ColumnsSelectionDsl.CommonColsDocs.Examples]}
            
            
            @param [predicate] A [ColumnFilter function][org.jetbrains.kotlinx.dataframe.ColumnFilter] that takes a [ColumnReference][org.jetbrains.kotlinx.dataframe.columns.ColumnReference] and returns a [Boolean].
            @return A [ColumnSet][org.jetbrains.kotlinx.dataframe.columns.ColumnSet] containing the columns that match the given [predicate].
            @setArg [CommonColsDocs.Examples]
            
            `// although these can be shortened to just the `[colsOf][colsOf]` call`
            
            `df.`[select][select]` { `[colsOf][colsOf]`<`[String][String]`>().`[cols][cols]` { "e" `[in\][String.contains\]` it.`[name][ColumnPath.name]`() } }`
            
            `df.`[select][select]` { `[colsOf][colsOf]`<`[String][String]`>()`[`[`][cols]`{ it.`[any\][ColumnWithPath.any\]` { it == "Alice" } }`[`]`][cols]` }`
            
            `// identity call, same as `[all][all]
            
            `df.`[select][select]` { `[colsOf][colsOf]`<`[String][String]`>().`[cols][cols]`() }`
            
            @see [all\]
            
        """.trimIndent()

//        kdoc1.findInlineTagNamesInDocContent().toSet() shouldBe expectedInline
//        kdoc1.findBlockTagNamesInDocContent().toSet() shouldBe expectedBlock
        println(kdoc1.splitDocContentPerBlock().joinToString("\n.............................................\n"))
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