package nl.jolanrensen.docProcessor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test

class TestDocContent {

    @Test
    fun `Simple doc`() {
        val kdoc = """
            /**
             *       Hello World!
             * 
             * @see [com.example.plugin.KdocIncludePlugin]
             */
        """.trimIndent()

        val expected = "\n      Hello World!\n\n@see [com.example.plugin.KdocIncludePlugin]\n"

        kdoc.getDocContentOrNull() shouldBe expected

        kdoc.getDocContentOrNull()?.toDoc() shouldBe kdoc
    }

    @Test
    fun `Simple doc no surrounding newlines 1`() {
        val kdoc = """
            /** Hello World!
             * 
             * @see [com.example.plugin.KdocIncludePlugin] */
        """.trimIndent()

        val expected = "Hello World!\n\n@see [com.example.plugin.KdocIncludePlugin]"

        kdoc.getDocContentOrNull() shouldBe expected

        kdoc.getDocContentOrNull()?.toDoc() shouldBe kdoc
    }

    @Test
    fun `Simple doc no surrounding newlines 2`() {
        val kdoc = """
            /** Hello World!
             * @see [com.example.plugin.KdocIncludePlugin] */
        """.trimIndent()

        val expected = "Hello World!\n@see [com.example.plugin.KdocIncludePlugin]"

        kdoc.getDocContentOrNull() shouldBe expected

        kdoc.getDocContentOrNull()?.toDoc() shouldBe kdoc
    }

    @Test
    fun `Single line doc`() {
        val kdoc = """
            /** Hello World! */
        """.trimIndent()

        val expected = "Hello World!"

        kdoc.getDocContentOrNull() shouldBe expected

        kdoc.getDocContentOrNull()?.toDoc() shouldBe kdoc
    }

    @Test
    fun `Single line doc with newlines`() {
        val kdoc = """
            /**
             * Hello World!
             */
        """.trimIndent()

        val expected = "\nHello World!\n"

        kdoc.getDocContentOrNull() shouldBe expected

        kdoc.getDocContentOrNull()?.toDoc() shouldBe kdoc
    }

    @Test
    fun `Wrongly formatted doc`() {
        val kdoc = """
            /**
             *Hello World!
             */
        """.trimIndent()

        val expected = "\nHello World!\n"

        kdoc.getDocContentOrNull() shouldBe expected

        val kdoca = """
            /**
             * Hello World!
             */
        """.trimIndent()

        kdoc.getDocContentOrNull()?.toDoc() shouldBe kdoca
    }

    @Test
    fun `Doc inside doc`() {
        val kdoc = """
            /**
             * Hello World! /** Some doc inside the doc */
             */
        """.trimIndent()

        val expected = "\nHello World! /** Some doc inside the doc */\n"

        kdoc.getDocContentOrNull() shouldBe expected

        kdoc.getDocContentOrNull()?.toDoc() shouldBe kdoc
    }

    @Test
    fun `Wrong doc`() {
        val kdoc = """
            
            /**
             * Wrong kdoc
             */
             
        """.trimIndent()

        kdoc.getDocContentOrNull() shouldBe null
    }

    @Test
    fun `Empty doc`() {
        val kdoc = """
            /** */
        """.trimIndent()

        val expected = ""

        kdoc.getDocContentOrNull() shouldBe expected
        kdoc.getDocContentOrNull()?.toDoc() shouldBe kdoc
    }

    @Test
    fun `Empty doc newlines`() {
        val kdoc = """
            /**
             * 
             */
        """.trimIndent()

        val expected = "\n\n"

        kdoc.getDocContentOrNull() shouldBe expected
        kdoc.getDocContentOrNull()?.toDoc() shouldBe kdoc
    }
}