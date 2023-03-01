@file:Suppress("FunctionName")

package nl.jolanrensen.docProcessor

import io.kotest.assertions.throwables.shouldThrowAny
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestInclude : DocProcessorFunctionalTest(name = "include") {

    private val processors = listOf("INCLUDE_DOC_PROCESSOR")

    @Test
    fun `Test include with and without package`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * @include [helloWorld]
             * @include [com.example.plugin.helloWorld]  
             */
            fun helloWorld2() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * Hello World!
             * 
             * Hello World!
             */
            fun helloWorld2() {}
            """.trimIndent()


        testContentSingleFile(
            content = content,
            expectedOutput = expectedOutput,
            processors = processors,
            packageName = "com.example.plugin",
        )
    }

    @Test
    fun `Test include no package`() {
        @Language("kt")
        val content = """
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * @include [helloWorld]
             */
            fun helloWorld2() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /** Hello World! */
            fun helloWorld2() {}
            """.trimIndent()

        testContentSingleFile(
            content = content,
            expectedOutput = expectedOutput,
            processors = processors,
            packageName = "",
        )
    }

    @Test
    fun `Test transitive include`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /** Hello World! */
            fun helloWorld() {}
            
            /** @include [helloWorld] */
            fun helloWorld2() {}

            /** @include [helloWorld2] */
            fun helloWorld3() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /** Hello World! */
            fun helloWorld() {}
            
            /** Hello World! */
            fun helloWorld2() {}
            
            /** Hello World! */
            fun helloWorld3() {}
            """.trimIndent()


        testContentSingleFile(
            content = content,
            expectedOutput = expectedOutput,
            processors = processors,
            packageName = "com.example.plugin",
        )
    }

    @Test
    fun `Test inline include`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /** Hello World! */
            fun helloWorld() {}
            
            /** @include [helloWorld] {@include [helloWorld]} */
            fun helloWorld2() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /** Hello World! */
            fun helloWorld() {}
            
            /** Hello World! Hello World! */
            fun helloWorld2() {}
            """.trimIndent()


        testContentSingleFile(
            content = content,
            expectedOutput = expectedOutput,
            processors = processors,
            packageName = "com.example.plugin",
        )
    }

    @Test
    fun `Test function overload include`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * @include [helloWorld]
             * @include [com.example.plugin.helloWorld]  
             */
            fun helloWorld(a: Int) {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            
            /**
             * Hello World!
             * 
             * Hello World!
             */
            fun helloWorld(a: Int) {}
            """.trimIndent()


        testContentSingleFile(
            content = content,
            expectedOutput = expectedOutput,
            processors = processors,
            packageName = "com.example.plugin",
        )
    }

    @Test
    fun `Test self reference include`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * @include [helloWorld]
             */
            fun helloWorld(a: Int) {}
            """.trimIndent()

        shouldThrowAny {
            testContentSingleFile(
                content = content,
                expectedOutput = "",
                processors = processors,
                packageName = "com.example.plugin",
            )
        }
    }

    @Test
    fun `Test unavailable reference include`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * @include [nothing]
             */
            fun helloWorld(a: Int) {}
            """.trimIndent()

        shouldThrowAny {
            testContentSingleFile(
                content = content,
                expectedOutput = "",
                processors = processors,
                packageName = "com.example.plugin",
            )
        }
    }
}