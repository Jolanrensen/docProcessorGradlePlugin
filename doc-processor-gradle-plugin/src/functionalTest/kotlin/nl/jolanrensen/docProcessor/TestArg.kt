@file:Suppress("FunctionName")

package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestArg : DocProcessorFunctionalTest(name ="arg") {

    private val processors = listOf(
        "INCLUDE_DOC_PROCESSOR",
        "INCLUDE_ARG_DOC_PROCESSOR"
    )

    // TODO

    @Test
    fun `Test simple arg kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World!
             * {@arg name World}
             */
            fun helloWorld() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello World!
             * 
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
    fun `Test Readme example`() {
        @Language("kt")
        val content = """
            package com.example.plugin

            /**
             * Hello World!
             * 
             * This is a large example of how the plugin will work from {@includeArg source}
             * 
             * @param name The name of the person to greet
             * @see [com.example.plugin.KdocIncludePlugin]
             * {@arg source Test1}
             */
            private interface Test1

            /**
             * Hello World 2!
             * @include [Test1] {@arg source Test2}
             */
            @AnnotationTest(a = 24)
            private interface Test2

            /** 
             * Some extra text
             * @include [Test2] {@arg source someFun} */
            fun someFun() {
                println("Hello World!")
            }

            /** {@include [com.example.plugin.Test2]}{@arg source someMoreFun} */
            fun someMoreFun() {
                println("Hello World!")
            }
        """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin

            /**
             * Hello World!
             * 
             * This is a large example of how the plugin will work from Test1
             * 
             * @param name The name of the person to greet
             * @see [com.example.plugin.KdocIncludePlugin]
             * 
             */
            private interface Test1

            /**
             * Hello World 2!
             * Hello World!
             * 
             * This is a large example of how the plugin will work from Test2
             * 
             * @param name The name of the person to greet
             * @see [com.example.plugin.KdocIncludePlugin][com.example.plugin.KdocIncludePlugin]
             *  
             */
            @AnnotationTest(a = 24)
            private interface Test2

            /**
             * Some extra text
             * Hello World 2!
             * Hello World!
             * 
             * This is a large example of how the plugin will work from someFun
             * 
             * @param name The name of the person to greet
             * @see [com.example.plugin.KdocIncludePlugin][com.example.plugin.KdocIncludePlugin]
             */
            fun someFun() {
                println("Hello World!")
            }

            /** Hello World 2!
             * Hello World!
             * 
             * This is a large example of how the plugin will work from someMoreFun
             * 
             * @param name The name of the person to greet
             * @see [com.example.plugin.KdocIncludePlugin][com.example.plugin.KdocIncludePlugin]
             */
            fun someMoreFun() {
                println("Hello World!")
            }
        """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }
}