package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
import nl.jolanrensen.docProcessor.defaultProcessors.INCLUDE_DOC_PROCESSOR
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestExcludeFromSources : DocProcessorFunctionalTest(name = "excl") {

    private val processors = listOf(
        ::INCLUDE_DOC_PROCESSOR
    ).map { it.name }

    @Language("kt")
    private val annotationDef = """
        package com.example.plugin
        
        @Target(
            CLASS,
            ANNOTATION_CLASS,
            TYPE_PARAMETER,
            PROPERTY,
            FIELD,
            LOCAL_VARIABLE,
            VALUE_PARAMETER,
            CONSTRUCTOR,
            FUNCTION,
            PROPERTY_GETTER,
            PROPERTY_SETTER,
            TYPE,
            TYPEALIAS,
        )
        annotation class ExcludeFromSources""".trimIndent()

    @Test
    fun `Simple exclusion test`() {

        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World
             */
            @ExcludeFromSources interface HelloWorld
            
            /**
             * {@include [HelloWorld]}!
             */
            fun helloWorld() {}
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/ExcludeFromSources.kt",
                    content = annotationDef,
                ),
            ),
        ) shouldBe expectedOutput
    }
}