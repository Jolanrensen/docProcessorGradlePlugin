package nl.jolanrensen.kdocInclude

import io.kotest.matchers.shouldBe
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Test

class KdocIncludePluginTest {
    @Test
    fun pluginRegistersATask() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("nl.jolanrensen.kdocInclude")

        // Verify the result
        Assert.assertNotNull(project.tasks.findByName("processKdocInclude"))
    }

    @Test
    fun `Test Kdoc utils`() {
        val kdoc1 = """
            /**
             * Hello World!
             * 
             * @see [com.example.plugin.KdocIncludePlugin]
             */
        """.trimIndent()

        kdoc1.getKdocContent().toKdoc() shouldBe kdoc1

        val kdoc2 = """
            /** Hello World!
             * 
             * @see [com.example.plugin.KdocIncludePlugin] */
        """.trimIndent()

        kdoc2.getKdocContent().toKdoc() shouldBe kdoc2

        val kdoc3 = """
            /** Hello World!
             * @see [com.example.plugin.KdocIncludePlugin] */
        """.trimIndent()

        kdoc3.getKdocContent().toKdoc() shouldBe kdoc3

        val kdoc4 = """
            /** Hello World! */
        """.trimIndent()

        kdoc4.getKdocContent().toKdoc() shouldBe kdoc4

        val kdoc5 = """
            /**
             * Hello World!
             */
        """.trimIndent()

        kdoc5.getKdocContent().toKdoc() shouldBe kdoc5
    }


    @Test
    fun `Test get class name from string`() {
        val a = getSourceName("""
            private class TestClass {
        """.trimIndent())

        a shouldBe "TestClass"

        val b = getSourceName("""
            internal object internal : Something, A {
        """.trimIndent())

        b shouldBe "internal"

        val c = getSourceName("""
            class private
        """.trimIndent())

        c shouldBe "private"

        val d = getSourceName("""
            public object public {
        """.trimIndent())

        d shouldBe "public"

        val e = getSourceName("""
            class `public`
        """.trimIndent())

        e shouldBe "`public`"

        val f = getSourceName("""
            @Test @`A`(`@Test` = (123) ) object `@Test` 
        """.trimIndent())

        f shouldBe "`@Test`"

        val g = getSourceName("""
            class private(val a: Int, val b: Private) : Iets {
        """.trimIndent())

        g shouldBe "private"

        val h = getSourceName("""
            @Test @`A`(`@werwerjkwent` = (123) ) object `@Test` 
        """.trimIndent())

        h shouldBe "`@Test`"
    }

    @Test
    fun `Test remove annotations`() {
        val a = """
            @Test @`A`(`@Test` = (123) ) object `@Test` 
        """.trimIndent()

        a.removeAnnotations() shouldBe "object `@Test`"
    }
}


