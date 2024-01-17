@file:Suppress("FunctionName")

package nl.jolanrensen.docProcessor

import io.kotest.matchers.shouldBe
import nl.jolanrensen.docProcessor.defaultProcessors.ArgDocProcessor
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestArg : DocProcessorFunctionalTest(name = "arg") {

    private val processors = listOf(
        "INCLUDE_DOC_PROCESSOR",
        "ARG_DOC_PROCESSOR",
        "COMMENT_DOC_PROCESSOR",
        "REMOVE_ESCAPE_CHARS_PROCESSOR",
    )

    @Test
    fun `Simple arg`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello World!
             * {@setArg name World}
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
    fun `Simple includeArg with arg`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello {@getArg name}!
             * {@setArg name World}
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
    fun `Arg not present for includeArg`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello {@getArg name}!
             */
            fun helloWorld() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello {@getArg name}!
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
    fun `Arg order inline`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello {@getArg name}!
             * {@setArg name Everyone}
             * {@setArg name World}
             */
            fun helloWorld() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /**
             * Hello World!
             *
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
    fun `Arg order block`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello {@getArg name}!
             * @setArg name Everyone
             * @setArg name World
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
        ) shouldBe expectedOutput
    }

    @Test
    fun `Arg order block and inline`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * Hello {@getArg name}!
             * @setArg name World
             * @comment This comment ensures that the arg does not have a newline at the end.
             * {@setArg name Everyone}
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
        ) shouldBe expectedOutput
    }

    @Test
    fun `Reference key simple kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin

            interface Key
            
            /**
             * Hello {@getArg [Key]}!
             * {@setArg [Key] World}
             */
            fun helloWorld() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin

            interface Key
            
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

    /**
     * Hello ${aas}!
     * {@setArg [Key] World}
     */
    @Test
    fun `Reference key simple kotlin ${} notation`() {
        @Language("kt")
        val content = """
            package com.example.plugin

            interface Key
            
            /**
             * Hello ${'$'}{[Key]}!
             * {@setArg [Key] World}
             */
            fun helloWorld() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin

            interface Key
            
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

    /**
     * Hello $[Key]
     * {@setArg [Key] World!}
     */
    @Test
    fun `Reference key simple kotlin $ notation`() {
        @Language("kt")
        val content = """
            package com.example.plugin

            interface Key
            
            /**
             * Hello ${'$'}[Key]
             * {@setArg [Key] World!}
             */
            fun helloWorld() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin

            interface Key
            
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

    /**
     * Hello $[Aliased Key][Key][erntjkrent]
     * Hello $[Aliased Key][Key]
     * Hello $[Aliased Key] a
     * Hello $Aliased{@param }
     * Hello $Aliased {@param }
     * {@setArg [Key] World!}
     */
    @Test
    fun `Reference key kotlin $ notation with aliased ref keys`() {
        @Language("kt")
        val content = """
            package com.example.plugin

            interface Key
            
            /**
             * {@setArg a b}
             * {@setArg ${'$'}a World!}
             * Hello ${'$'}b
             */
            fun helloWorld() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin

            interface Key
            
            /**
             * 
             * 
             * Hello World!
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
    fun `Escaping character reference key kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin

            interface Key
            
            /**
             * Hello {@getArg [Key]}!
             * {@setArg [Key] {World\}}
             */
            fun helloWorld() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin

            interface Key
            
            /**
             * Hello {World}!
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
    fun `Include from other file kotlin`() {
        @Language("kt")
        val otherFile = """
            package com.example.plugin
            
            interface Key

            /**
             * Hello {@getArg [Key]}!
             */
            fun helloWorld() {}
            """.trimIndent()

        @Language("kt")
        val content = """
            package com.example.plugin
            
            /** @include [helloWorld] {@setArg [Key] World} */
            fun helloWorld2() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /** Hello World! */
            fun helloWorld2() {}
            """.trimIndent()

        processContent(
            content = content,
            additionals = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/Test2.kt",
                    content = otherFile,
                )
            ),
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Readme example`() {
        @Language("kt")
        val content = """
            package com.example.plugin

            /**
             * Hello World!
             * 
             * This is a large example of how the plugin will work from {@getArg source}
             * 
             * @param name The name of the person to greet
             * @see [com.example.plugin.KdocIncludePlugin]
             * {@setArg source Test1}
             */
            private interface Test1

            /**
             * Hello World 2!
             * @include [Test1] {@setArg source Test2}
             */
            @AnnotationTest(a = 24)
            private interface Test2

            /**
             * Some extra text
             * @include [Test2] {@setArg source someFun} */
            fun someFun() {
                println("Hello World!")
            }

            /** {@include [com.example.plugin.Test2]}{@setArg source someMoreFun} */
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
             * @see [com.example.plugin.KdocIncludePlugin]
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
             * @see [com.example.plugin.KdocIncludePlugin]
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
             * @see [com.example.plugin.KdocIncludePlugin]
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

    @Test
    fun `replace dollar notation`() {
        with(ArgDocProcessor()) {
            "\${a}".replaceDollarNotation() shouldBe "{@getArg a}"
            " \${a}".replaceDollarNotation() shouldBe " {@getArg a}"
            "a\${a}".replaceDollarNotation() shouldBe "a{@getArg a}"
            "a\${a}a".replaceDollarNotation() shouldBe "a{@getArg a}a"
            "a\${test\\test}".replaceDollarNotation() shouldBe "a{@getArg test\\test}"
            "a\${test test}".replaceDollarNotation() shouldBe "a{@getArg test test}"
            "\${[test with spaces][function]}".replaceDollarNotation() shouldBe "{@getArg [test with spaces][function]}"
            "\${hi \${test} \${\$hi}}".replaceDollarNotation() shouldBe "{@getArg hi {@getArg test} {@getArg {@getArg hi}}}"

            "\\\${a}".replaceDollarNotation() shouldBe "\\\${a}"
//            "\$\\{a}".replaceDollarNotation() shouldBe "\$\\{a}"
//            "\${a\\}".replaceDollarNotation() shouldBe "\${a\\}"

            "\$key no more key".replaceDollarNotation() shouldBe "{@getArg key} no more key"
            "\$[key] \$[key2] \$[key3]".replaceDollarNotation() shouldBe "{@getArg [key]} {@getArg [key2]} {@getArg [key3]}"
            "a\${a}a\${a}a".replaceDollarNotation() shouldBe "a{@getArg a}a{@getArg a}a"
            "\$[anything [] goes {}[a][test] ][replaceDollarNotation]".replaceDollarNotation() shouldBe "{@getArg [anything [] goes {}[a][test] ][replaceDollarNotation]}"
            "\$[hello[[[`]]]` there][replaceDollarNotation]".replaceDollarNotation() shouldBe "{@getArg [hello[[[`]]]` there][replaceDollarNotation]}"
            "{@setArg \$a test}".replaceDollarNotation() shouldBe "{@setArg {@getArg a} test}"
        }
    }
}