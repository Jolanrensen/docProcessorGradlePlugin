package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestTodo : DocProcessorFunctionalTest(name = "todo") {

    private val processors = listOf(
        "TODO_DOC_PROCESSOR",
    )

    @Test
    fun `Test simple`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /** TODO */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }
}