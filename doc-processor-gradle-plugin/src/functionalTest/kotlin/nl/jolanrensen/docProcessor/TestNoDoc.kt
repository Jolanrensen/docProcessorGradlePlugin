package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestNoDoc : DocProcessorFunctionalTest(name = "no-doc") {

    private val processors = listOf(
        "NO_DOC_PROCESSOR",
    )

    @Test
    fun `Test simple no docs`() {

        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            fun helloWorld() {}
            """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test indented no docs`() {

        @Language("kt")
        val content = """
            package com.example.plugin
            
                    /**
                     * Hello World!
                     */
            fun helloWorld() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            fun helloWorld() {}
            """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test no docs without start newline`() {

        @Language("kt")
        val content = """
            /**
             * Hello World!
             */
            fun helloWorld() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            fun helloWorld() {}
            """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test no docs without end newline`() {

        @Language("kt")
        val content = """
            /**
             * Hello World!
             */fun helloWorld() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            fun helloWorld() {}
            """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }
}
