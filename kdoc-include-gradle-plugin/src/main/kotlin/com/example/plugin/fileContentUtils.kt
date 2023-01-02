package com.example.plugin

import org.intellij.lang.annotations.Language

// For ((a, (b)), c) etc.
private val encompassingBracketsRegex =
    Regex("""(?=\()(?:(?=.*?\((?!.*?\1)(.*\)(?!.*\2).*))(?=.*?\)(?!.*?\2)(.*)).)+?.*?(?=\1)[^\(]*(?=\2$)""")
private val packageRegex = Regex("""package(\s+)(.+)(\s+)(;?)""")
private val nameRegex = Regex("""((`[^`]+`)|([a-zA-Z][a-zA-Z0-9]*))""")

// For @`A`(`@Test` = ((123)))
val annotationRegex1 =
    Regex("""@`[^`]+`${encompassingBracketsRegex.pattern}""")

// For @Test(`@Test` = ((123)))
val annotationRegex2 =
    Regex("""@[a-zA-Z][a-zA-Z0-9]*${encompassingBracketsRegex.pattern}""")

// For @Test or @`A`
val annotationRegex3 = Regex("""@((`[^`]+`)|([a-zA-Z][a-zA-Z0-9]*))""")

fun getPackageName(@Language("kt") fileContent: String): String =
    packageRegex.find(fileContent)?.groupValues?.get(2) ?: ""

fun String.removeAnnotations(): String {
    var res = this
    while (res.startsWith('@')) {
        val annotationPart = annotationRegex1.matchAt(res, 0)?.value
            ?: annotationRegex2.matchAt(res, 0)?.value
            ?: annotationRegex3.matchAt(res, 0)?.value!!
        res = res.removePrefix(annotationPart).trim()
    }
    return res
}

fun getSourceName(source: String): String = source
    .trim()
    .removeAnnotations()
    .trim()
    .removePrefix("public")
    .removePrefix("private")
    .removePrefix("internal")
    .removePrefix("protected")
    .removePrefix("open")
    .removePrefix("abstract")
    .removePrefix("sealed")
    .removePrefix("final")
    .removePrefix("expect")
    .removePrefix("actual")
    .removePrefix("external")
    .removePrefix("enum")
    .removePrefix("annotation")
    .removePrefix("companion")
    .removePrefix("data")
    .removePrefix("value")
    .removePrefix("inline")
    .removePrefix("inner")
    .trim()
    .removePrefix("object")
    .removePrefix("class")
    .removePrefix("interface")
    .trim()
    .let { nameRegex.matchAt(it, 0)!!.value }




