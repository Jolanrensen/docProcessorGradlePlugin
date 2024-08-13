package nl.jolanrensen.docProcessor

import com.intellij.openapi.util.TextRange

fun TextRange.toIntRange(): IntRange = startOffset until endOffset

fun IntRange.toTextRange(): TextRange = TextRange(start, endInclusive + 1)
