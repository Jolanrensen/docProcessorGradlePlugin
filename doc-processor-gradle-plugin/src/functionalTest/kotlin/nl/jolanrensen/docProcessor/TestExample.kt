package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestExample : DocProcessorFunctionalTest(name = "example") {

    private val processors = listOf(
        "\"nl.jolanrensen.docProcessor.defaultProcessors.ExampleDocProcessor\""
    )

    @Test
    fun `Test example block tag`() {

        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * @example Hello World!
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hi from the example doc processor! Here's the content after the @example tag: "Hello World!"
             */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test example inline tag`() {

        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * {@example Hello World!}
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hi from the example doc processor! Here's the content after the @example tag: "Hello World!"
             */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

}