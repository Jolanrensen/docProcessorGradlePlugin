package com.example.plugin

/**
 * Returns the actual content of the KDoc comment
 */
fun String.getKdocContent() = this
    .split('\n')
    .joinToString("\n") {
        it
            .trim()
            .removePrefix("/**")
            .removeSuffix("*/")
            .removePrefix("*")
            .trim()
    }


/**
 * Turns multi-line String into valid kdoc.
 */
fun String.toKdoc(indent: Int = 0) = this
    .split('\n')
    .toMutableList()
    .let {
        it[0] = "/** ${it[0]}".trim()

        val lastIsBlank = it.last().isBlank()

        it[it.lastIndex] = it[it.lastIndex].trim() + " */"

        it.mapIndexed { index, s ->
            buildString {
                if (index != 0) append("\n")
                append(" ".repeat(indent))

                if (!(index == 0 || index == it.lastIndex && lastIsBlank)) {
                    append(" * ")
                }
                append(s)
            }
        }.joinToString("")
    }