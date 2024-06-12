package nl.jolanrensen.docProcessor

import com.intellij.openapi.util.TextRange

fun TextRange.toIntRange(): IntRange = startOffset until endOffset

fun IntRange.toTextRange(): TextRange = TextRange(start, endInclusive + 1)


fun main() {
    println(1..5 == 1..5)
    println((1..5).toTextRange() == (1..5).toTextRange())
}