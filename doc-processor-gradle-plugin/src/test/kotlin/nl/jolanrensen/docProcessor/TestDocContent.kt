package nl.jolanrensen.docProcessor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test

class TestDocContent {

    @Test
    fun `KDoc 1`() {
        val kdoc = """
            /**
             *       Hello World!
             * 
             * @see [com.example.plugin.KdocIncludePlugin]
             */
        """.trimIndent()

        val expected = "\n      Hello World!\n\n@see [com.example.plugin.KdocIncludePlugin]\n"

        kdoc.getDocContent() shouldBe expected

        kdoc.getDocContent().toDoc() shouldBe kdoc
    }

    @Test
    fun `KDoc 2`() {
        val kdoc = """
            /** Hello World!
             * 
             * @see [com.example.plugin.KdocIncludePlugin] */
        """.trimIndent()

        val expected = "Hello World!\n\n@see [com.example.plugin.KdocIncludePlugin]"

        kdoc.getDocContent() shouldBe expected

        kdoc.getDocContent().toDoc() shouldBe kdoc
    }

    @Test
    fun `KDoc 3`() {
        val kdoc = """
            /** Hello World!
             * @see [com.example.plugin.KdocIncludePlugin] */
        """.trimIndent()

        val expected = "Hello World!\n@see [com.example.plugin.KdocIncludePlugin]"

        kdoc.getDocContent() shouldBe expected

        kdoc.getDocContent().toDoc() shouldBe kdoc
    }

    @Test
    fun `KDoc 4`() {
        val kdoc = """
            /** Hello World! */
        """.trimIndent()

        val expected = "Hello World!"

        kdoc.getDocContent() shouldBe expected

        kdoc.getDocContent().toDoc() shouldBe kdoc
    }

    @Test
    fun `KDoc 5`() {
        val kdoc = """
            /**
             * Hello World!
             */
        """.trimIndent()

        val expected = "\nHello World!\n"

        kdoc.getDocContent() shouldBe expected

        kdoc.getDocContent().toDoc() shouldBe kdoc
    }

    @Test
    fun `KDoc 6`() {
        val kdoc = """
            /**
             *Hello World!
             */
        """.trimIndent()

        val expected = "\nHello World!\n"

        kdoc.getDocContent() shouldBe expected

        val kdoca = """
            /**
             * Hello World!
             */
        """.trimIndent()

        kdoc.getDocContent().toDoc() shouldBe kdoca
    }

    @Test
    fun `KDoc 7`() {
        val kdoc = """
            /**
             * Hello World! /** Some doc inside the doc */
             */
        """.trimIndent()

        val expected = "\nHello World! /** Some doc inside the doc */\n"

        kdoc.getDocContent() shouldBe expected

        kdoc.getDocContent().toDoc() shouldBe kdoc
    }

    @Test
    fun `KDoc 8`() {
        val kdoc = """
            
            /**
             * Wrong kdoc
             */
             
        """.trimIndent()

        shouldThrow<IllegalArgumentException> {
            kdoc.getDocContent()
        }
    }

    @Test
    fun `KDoc 9`() {
        val kdoc = """
            /**
             * 
             */
        """.trimIndent()

        val expected = "\n\n"

        kdoc.getDocContent() shouldBe expected
        kdoc.getDocContent().toDoc() shouldBe kdoc
    }
}