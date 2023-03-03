@file:Suppress("FunctionName")

package nl.jolanrensen.docProcessor

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.Test

class TestInclude : DocProcessorFunctionalTest(name = "include") {

    private val processors = listOf(
        "INCLUDE_DOC_PROCESSOR",
    )

    @Test
    fun `Test include with and without package kotlin`() {
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
             * Hello World!
             */
            fun helloWorld2() {}
            """.trimIndent()


        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test include with and without package java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * Hello World!
                 */
                public void helloWorld() {}

                /**
                 * @include {@link Test#helloWorld}
                 * @include {@link com.example.plugin.Test#helloWorld}  
                 */
                public void helloWorld2() {}
            }
            """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * Hello World!
                 */
                public void helloWorld() {}
            
                /**
                 * Hello World!
                 * Hello World!
                 */
                public void helloWorld2() {}
            }
            """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            javaOrKotlin = JavaOrKotlin.JAVA,
        ) shouldBe expectedOutput
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

        processContent(
            content = content,
            packageName = "",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test transitive include kotlin`() {
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


        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test expanding link kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /** 
              * Hello World! 
              * [Some aliased link][helloWorld2] 
              * [helloWorld]
              */
            fun helloWorld() {}
            
            /** @include [helloWorld] */
            fun helloWorld2() {}
            """.trimIndent()

        @Language("kt")
        val expectedOutput = """
            package com.example.plugin
            
            /** 
              * Hello World! 
              * [Some aliased link][helloWorld2] 
              * [helloWorld]
              */
            fun helloWorld() {}
            
            /**
             * Hello World! 
             * [Some aliased link][com.example.plugin.helloWorld2] 
             * [helloWorld][com.example.plugin.helloWorld]
             */
            fun helloWorld2() {}
            """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test transitive include java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test {
            
                /** Hello World! */
                public void helloWorld() {}
            
                /** @include {@link Test#helloWorld} */
                public void helloWorld2() {}
            
                /** @include {@link Test#helloWorld2} */
                public void helloWorld3() {}
            }
            """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            public class Test {
            
                /** Hello World! */
                public void helloWorld() {}
            
                /** Hello World! */
                public void helloWorld2() {}
            
                /** Hello World! */
                public void helloWorld3() {}
            }
            """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            javaOrKotlin = JavaOrKotlin.JAVA,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test inline include kotlin`() {
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


        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test inline include java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test {
            
                /** Hello World! */
                public void helloWorld() {}
            
                /** @include {@link Test#helloWorld} {@include {@link Test#helloWorld}} */
                public void helloWorld2() {}
            }
            """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            public class Test {
            
                /** Hello World! */
                public void helloWorld() {}
            
                /** Hello World! Hello World! */
                public void helloWorld2() {}
            }
            """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            javaOrKotlin = JavaOrKotlin.JAVA,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test function overload include kotlin`() {
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
             * Hello World!
             */
            fun helloWorld(a: Int) {}
            """.trimIndent()


        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test function overload include java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * Hello World!
                 */
                public void helloWorld() {}
            
                /**
                 * @include {@link Test#helloWorld}
                 * @include {@link com.example.plugin.Test#helloWorld}  
                 */
                public void helloWorld(int a) {}
            }
            """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * Hello World!
                 */
                public void helloWorld() {}
            
                /**
                 * Hello World!
                 * Hello World!
                 */
                public void helloWorld(int a) {}
            }
            """.trimIndent()

        processContent(
            content = content,
            packageName = "com.example.plugin",
            processors = processors,
            javaOrKotlin = JavaOrKotlin.JAVA,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test self reference include kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * @include [helloWorld]
             */
            fun helloWorld(a: Int) {}
            """.trimIndent()

        shouldThrowAny {
            processContent(
                content = content,
                packageName = "com.example.plugin",
                processors = processors,
            )
        }
    }

    @Test
    fun `Test self reference include java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * @include {@link Test#helloWorld}
                 */
                public void helloWorld(int a) {}
            }
            """.trimIndent()

        shouldThrowAny {
            processContent(
                content = content,
                packageName = "com.example.plugin",
                processors = processors,
                javaOrKotlin = JavaOrKotlin.JAVA,
            )
        }
    }

    @Test
    fun `Test unavailable reference include kotlin`() {
        @Language("kt")
        val content = """
            package com.example.plugin
            
            /**
             * @include [nothing]
             */
            fun helloWorld(a: Int) {}
            """.trimIndent()

        shouldThrowAny {
            processContent(
                content = content,
                packageName = "com.example.plugin",
                processors = processors,
            )
        }
    }

    @Test
    fun `Test unavailable reference include java`() {
        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * @include {@link Test#nothing}
                 */
                public void helloWorld(int a) {}
            }
            """.trimIndent()

        shouldThrowAny {
            processContent(
                content = content,
                packageName = "com.example.plugin",
                processors = processors,
                javaOrKotlin = JavaOrKotlin.JAVA,
            )
        }
    }

    @Test
    fun `Test multiple files kotlin`() {
        @Language("kt")
        val otherFile = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            """.trimIndent()

        @Language("kt")
        val content = """
            package com.example.plugin
            
            /** @include [helloWorld] */
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
            additionalFiles = listOf(
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
    fun `Test multiple files java`() {
        @Language("java")
        val otherFile = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * Hello World!
                 */
                public void helloWorld() {}
            }
            """.trimIndent()

        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test2 {
            
                /** @include {@link Test#helloWorld} */
                public void helloWorld2() {}
            }
            """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            public class Test2 {
            
                /** Hello World! */
                public void helloWorld2() {}
            }
            """.trimIndent()

        processContent(
            content = content,
            fileName = "Test2",
            packageName = "com.example.plugin",
            additionalFiles = listOf(
                AdditionalFile(
                    relativePath = "src/main/java/com/example/plugin/Test.java",
                    content = otherFile,
                )
            ),
            processors = processors,
            javaOrKotlin = JavaOrKotlin.JAVA,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test multiple files kotlin to java`() {
        @Language("kt")
        val otherFile = """
            package com.example.plugin
            
            /**
             * Hello World!
             */
            fun helloWorld() {}
            """.trimIndent()

        @Language("java")
        val content = """
            package com.example.plugin;
            
            public class Test2 {
            
                /** @include {@link TestKt#helloWorld} */
                public void helloWorld2() {}
            }
            """.trimIndent()

        @Language("java")
        val expectedOutput = """
            package com.example.plugin;
            
            public class Test2 {
            
                /** Hello World! */
                public void helloWorld2() {}
            }
            """.trimIndent()

        processContent(
            content = content,
            fileName = "Test2",
            packageName = "com.example.plugin",
            additionalFiles = listOf(
                AdditionalFile(
                    relativePath = "src/main/kotlin/com/example/plugin/Test.kt",
                    content = otherFile,
                )
            ),
            processors = processors,
            javaOrKotlin = JavaOrKotlin.JAVA,
        ) shouldBe expectedOutput
    }

    @Test
    fun `Test multiple files java to kotlin`() {
        @Language("java")
        val otherFile = """
            package com.example.plugin;
            
            public class Test {
            
                /**
                 * Hello World!
                 */
                public void helloWorld() {}
            }
            """.trimIndent()

        @Language("kt")
        val content = """
            package com.example.plugin
            
            /** @include [Test.helloWorld] */
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
            packageName = "com.example.plugin",
            additionalFiles = listOf(
                AdditionalFile(
                    relativePath = "src/main/java/com/example/plugin/Test.java",
                    content = otherFile,
                )
            ),
            processors = processors,
        ) shouldBe expectedOutput
    }
}