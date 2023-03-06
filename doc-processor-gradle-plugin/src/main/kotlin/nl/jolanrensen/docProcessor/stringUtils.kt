package nl.jolanrensen.docProcessor

/**
 * Last index of not [char] moving from startIndex down to 0.
 * Returns 0 if char is not found (since last index looked at is 0).
 * Returns `this.size` if [char] is found at [startIndex].
 *
 * @receiver
 * @param char Char
 * @param startIndex Start index
 * @return
 */
fun String.lastIndexOfNot(char: Char, startIndex: Int = lastIndex): Int {
    for (i in startIndex downTo 0) {
        if (this[i] == char) return i + 1
    }
    return 0
}

/**
 * Removes "\" from the String, but only if it is not escaped.
 */
fun String.removeEscapeCharacters(escapeChars: List<Char> = listOf('\\')): String = buildString {
    var escapeNext = false
    for (char in this@removeEscapeCharacters) {
        if (escapeNext) {
            escapeNext = false
        } else if (char in escapeChars) {
            escapeNext = true
            continue
        }
        append(char)
    }
}

/**
 * Replaces multiple ranges with their respective replacements.
 * The replacements can be of any size.
 */
fun String.replaceRanges(rangeToReplacement: Map<IntRange, String>): String {
    val textRange = this.indices.associateWith { this[it].toString() }.toMutableMap()
    for ((range, replacement) in rangeToReplacement) {
        range.forEach(textRange::remove)
        textRange[range.first] = replacement
    }
    return textRange.toSortedMap().values.joinToString("")
}

/**
 * Replace all matches of [regex], even if they overlap a part of their match.
 * Matches are temporarily replaced with [intermediateReplacementChar] (so that we don't get an infinite loop dependent
 * on the result of [transform]) before being actually replaced with the result of [transform].
 */
@Deprecated("No use for it anymore?")
fun CharSequence.replaceAll(
    regex: Regex,
    limit: Int = 10_000,
    intermediateReplacementChar: Char = ' ', // must not match the regex
    transform: (MatchResult) -> CharSequence,
): String {
    var text = this.toString()

    var i = 0
    val replacements = mutableMapOf<IntRange, String>()
    while (regex in text) {
        text = text.replace(regex) {
            val range = it.range
            replacements[range] = transform(it).toString()

            intermediateReplacementChar.toString().repeat(range.count())
                .also { require(regex !in it) { "intermediateReplacementChar must not match the regex" } }
        }

        if (i++ > limit) {
            println("WARNING: replaceWhilePresent limit reached for $regex in $this")
            break
        }
    }

    return text.replaceRanges(replacements)
}

fun String.indexOfFirstOrNullWhile(char: Char, startIndex: Int = 0, whileCondition: (Char) -> Boolean): Int? {
    try {
        for (i in startIndex until length) {
            if (this[i] == char) return i
            if (!whileCondition(this[i])) break
        }
        return null
    } catch (e: IndexOutOfBoundsException) {
        return null
    }
}

fun String.indexOfLastOrNullWhile(char: Char, startIndex: Int = lastIndex, whileCondition: (Char) -> Boolean): Int? {
    try {
        for (i in startIndex downTo 0) {
            if (this[i] == char) return i
            if (!whileCondition(this[i])) break
        }
        return null
    } catch (e: IndexOutOfBoundsException) {
        return null
    }
}

val IntRange.size get() = last - first + 1