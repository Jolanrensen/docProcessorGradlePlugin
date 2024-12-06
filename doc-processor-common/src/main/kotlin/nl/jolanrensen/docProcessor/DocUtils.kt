package nl.jolanrensen.docProcessor

import nl.jolanrensen.docProcessor.ReferenceState.INSIDE_ALIASED_REFERENCE
import nl.jolanrensen.docProcessor.ReferenceState.INSIDE_REFERENCE
import nl.jolanrensen.docProcessor.ReferenceState.NONE
import java.util.SortedMap
import kotlin.collections.ArrayDeque

/**
 * Just the contents of the comment, without the `*`-stuff.
 */
@JvmInline
value class DocContent(val value: String) {
    override fun toString(): String = value
}

fun String.asDocContent(): DocContent = DocContent(this)

/**
 * The entire comment, including the `*`-stuff and potential leading/trailing spaces.
 */
@JvmInline
value class DocText(val value: String) {

    init {
        require(value.isNotBlank() && value.startsWith("/**") && value.endsWith("*/")) {
            "DocText must start with '/**' and end with '*/'"
        }
    }

    override fun toString(): String = value
}

fun String.asDocText(): DocText = DocText(this)

fun String.asDocTextOrNull(): DocText? =
    if (isNotBlank() && startsWith("/**") && endsWith("*/")) {
        DocText(this)
    } else {
        null
    }

// used to keep track of the current blocks
internal const val CURLY_BRACES = '{'
internal const val SQUARE_BRACKETS = '['
internal const val PARENTHESES = '('
internal const val ANGULAR_BRACKETS = '<'
internal const val BACKTICKS = '`'
internal const val DOUBLE_QUOTES = '"'
internal const val SINGLE_QUOTES = '\''

/**
 * Returns the actual content of the KDoc/Javadoc comment
 */
fun DocText.getDocContent(): DocContent = getDocContentWithMap().first

/**
 * Returns the actual content of the KDoc/Javadoc comment
 *
 * [Pair.second] contains the mapping from the indices of the result to the indices
 * of the same character in the original string: `result\[key\] == [this]\[value\]`
 */
fun DocText.getDocContentWithMap(): Pair<DocContent, List<Int>> {
    // result[key] == this@getDocContentWithMapOrNull[value]
    val resultToOriginalMap = mutableMapOf<Int, Int>()

    val lines = value.split('\n').withIndex()

    var originalCharIndex = 0
    var resultCharIndex = 0
    val result = lines.joinToString("\n") { (lineIndex, it) ->
        var line = it

        // adds the number of removed characters in the result line to originalCharIndex
        fun String.alsoUpdateOriginalCharIndex(): String = also { originalCharIndex += line.length - length }

        // start of the comment
        if (lineIndex == 0) {
            line = line.trimStart().removePrefix("/**")
                .alsoUpdateOriginalCharIndex()
        }
        // end of the comment
        if (lineIndex == lines.count() - 1) {
            val lastLine = line.trimStart()

            line = if (lastLine == "*/") {
                ""
            } else {
                lastLine.removePrefix("*").alsoUpdateOriginalCharIndex()
                    .removeSuffix("*/")
                    .removeSuffix(" ") // optional extra space at the end
            }
        }
        // middle of the comment (not start nor end)
        if (lineIndex != 0 && lineIndex != lines.count() - 1) {
            line = line.trimStart().removePrefix("*")
                .alsoUpdateOriginalCharIndex()
        }

        // remove optional extra space at the start
        line = line.removePrefix(" ")
            .alsoUpdateOriginalCharIndex()

        // update the map for all characters now in the result line
        for (j in line.indices) {
            resultToOriginalMap[resultCharIndex + j] = originalCharIndex + lineIndex + j
        }
        // ..and the \n character
        resultToOriginalMap[resultCharIndex + line.length] = originalCharIndex + lineIndex + line.length

        // update the two indices for the next iteration
        resultCharIndex += line.length + 1
        originalCharIndex += line.length

        line
    }

    // remove the final \n character
    resultToOriginalMap.remove(resultToOriginalMap.size - 1)

    return result.asDocContent() to resultToOriginalMap.values.toList()
}

/**
 * Turns multi-line String into valid KDoc/Javadoc.
 */
fun DocContent.toDocText(indent: Int = 0): DocText =
    this.value
        .split('\n')
        .toMutableList()
        .let {
            it[0] = if (it[0].isEmpty()) "/**" else "/** ${it[0]}"

            val lastIsBlank = it.last().isBlank()

            it[it.lastIndex] = it[it.lastIndex].trim() + " */"

            it.mapIndexed { index, s ->
                buildString {
                    if (index != 0) append("\n")
                    append(" ".repeat(indent))

                    if (!(index == 0 || index == it.lastIndex && lastIsBlank)) {
                        append(" *")
                        if (s.isNotEmpty()) append(" ")
                    }
                    append(s)
                }
            }.joinToString("")
        }.asDocText()

/**
 * Can retrieve the arguments of an inline- or block-tag.
 * Arguments are split by spaces, unless they are in a block of "{}", "[]", "()", "<>", "`", """, or "'".
 * Blocks "marks" are ignored if "\" escaped.
 */
fun String.getTagArguments(tag: String, numberOfArguments: Int): List<String> =
    getTagArgumentsWithRanges(tag, numberOfArguments).map { it.first }

/**
 * Can retrieve the arguments of an inline- or block-tag.
 * Arguments are split by spaces, unless they are in a block of "{}", "[]", "()", "<>", "`", """, or "'".
 * Blocks "marks" are ignored if "\" escaped.
 */
fun String.getTagArgumentsWithRanges(tag: String, numberOfArguments: Int): List<Pair<String, IntRange>> {
    require("@$tag" in this) { "Could not find @$tag in $this" }
    require(numberOfArguments > 0) { "numberOfArguments must be greater than 0" }

    var i = 0
    var content = this

    // remove inline tag stuff
    if (content.startsWith("{") && content.endsWith("}")) {
        content = content.removePrefix("{").removeSuffix("}")
        i++
    }

    val prevContentLength = content.length

    // remove leading spaces
    content = content.trimStart()

    // remove tag
    content = content.removePrefix("@$tag").trimStart()

    i += prevContentLength - content.length

    val arguments = buildList {
        var currentBlock = ""
        val blocksIndicators = mutableListOf<Char>()

        fun isDone(): Boolean = size >= numberOfArguments - 1

        fun isInCodeBlock() = BACKTICKS in blocksIndicators

        var escapeNext = false
        for (char in content) {
            when {
                escapeNext -> escapeNext = false

                char == '\\' -> escapeNext = true

                isDone() -> Unit

                char.isWhitespace() && blocksIndicators.isEmpty() -> {
                    if (currentBlock.isNotBlank()) {
                        this += Pair(
                            first = currentBlock,
                            second = i..<(i + currentBlock.length),
                        )
                        i += currentBlock.length
                    }
                    currentBlock = ""
                }

                char == '`' -> if (!blocksIndicators.removeAllElementsFromLast(BACKTICKS)) blocksIndicators += BACKTICKS
            }
            if (!isInCodeBlock()) {
                when (char) {
                    '{' -> blocksIndicators += CURLY_BRACES

                    '}' -> blocksIndicators.removeAllElementsFromLast(CURLY_BRACES)

                    '[' -> blocksIndicators += SQUARE_BRACKETS

                    ']' -> blocksIndicators.removeAllElementsFromLast(SQUARE_BRACKETS)

                    '(' -> blocksIndicators += PARENTHESES

                    ')' -> blocksIndicators.removeAllElementsFromLast(PARENTHESES)

                    '<' -> blocksIndicators += ANGULAR_BRACKETS

                    '>' -> blocksIndicators.removeAllElementsFromLast(ANGULAR_BRACKETS)

                    '"' -> if (!blocksIndicators.removeAllElementsFromLast(DOUBLE_QUOTES)) {
                        blocksIndicators += DOUBLE_QUOTES
                    }

                    '\'' -> if (blocksIndicators.removeAllElementsFromLast(SINGLE_QUOTES)) {
                        blocksIndicators += SINGLE_QUOTES
                    }

                    // TODO: issue #11: html tags
                }
            }
            if (isDone() || !(currentBlock.isEmpty() && char.isWhitespace())) {
                currentBlock += char
            }
        }

        this += Pair(
            first = currentBlock,
            second = i..<(i + currentBlock.length),
        )
    }

    val trimmedArguments = arguments.mapIndexed { i, (ogContent, ogRange) ->
        when (i) {
            // last argument will be kept as is, removing one "splitting" space if it starts with one
            arguments.lastIndex ->
                if (ogContent.startsWith(" ") || ogContent.startsWith("\t")) {
                    Pair(ogContent.drop(1), ogRange.first + 1..ogRange.last)
                } else {
                    Pair(ogContent, ogRange)
                }

            else -> { // other arguments will be trimmed at the start only. A newline counts as a space
                val trimmed = ogContent.removePrefix("\n").trimStart(' ', '\t')
                Pair(trimmed, ogRange.first + (ogContent.length - trimmed.length)..ogRange.last)
            }
        }
    }

    return trimmedArguments
}

fun String.getTagArgumentWithRangeByIndexOrNull(
    index: Int,
    tag: String,
    numberOfArguments: Int,
): Pair<String, IntRange>? = getTagArgumentsWithRanges(tag, numberOfArguments).getOrNull(index)

/**
 * Can retrieve the arguments of an inline- or block-tag.
 * Arguments are split by spaces, unless they are in a block of "{}", "[]", "()", "<>", "`", """, or "'".
 * Blocks "marks" are ignored if "\" escaped.
 *
 * @param tag The tag name, without the "@", will be removed after removing optional surrounding {}'s
 * @param numberOfArguments The number of arguments to retrieve. This will be the size of the @return list.
 *   The last argument will contain all remaining content, no matter if it can be split or not.
 * @param onRogueClosingChar Optional lambda that will be called when a '}', ']', ')', or '>' is found without respective
 *   opening char. Won't be triggered if '\' escaped.
 * @param isSplitter Defaults to `{ isWhitespace() }`. Can be used to change the splitting behavior.
 */
fun String.getTagArguments(
    tag: String,
    numberOfArguments: Int,
    onRogueClosingChar: (closingChar: Char, argument: Int, indexInArg: Int) -> Unit,
    isSplitter: Char.() -> Boolean,
): List<String> {
    require(numberOfArguments > 0) { "numberOfArguments must be greater than 0" }

    var content = this

    // remove inline tag stuff
    if (content.startsWith("{") && content.endsWith("}")) {
        content = content.removePrefix("{").removeSuffix("}")
    }

    // remove leading spaces
    content = content.trimStart { it.isSplitter() }

    // remove tag
    content = content.removePrefix("@").removePrefix(tag).trimStart { it.isSplitter() }

    val arguments = buildList {
        var currentBlock = ""
        val blocksIndicators = mutableListOf<Char>()

        fun isDone(): Boolean = size >= numberOfArguments - 1

        fun isInCodeBlock() = BACKTICKS in blocksIndicators

        var escapeNext = false
        for (char in content) {
            when {
                escapeNext -> {
                    escapeNext = false
                    continue
                }

                char == '\\' -> escapeNext = true

                isDone() -> Unit

                char.isSplitter() && blocksIndicators.isEmpty() -> {
                    if (!currentBlock.all { it.isSplitter() }) add(currentBlock)
                    currentBlock = ""
                }

                char == '`' -> if (!blocksIndicators.removeAllElementsFromLast(BACKTICKS)) blocksIndicators += BACKTICKS
            }
            if (!isInCodeBlock()) {
                when (char) {
                    '{' -> blocksIndicators += CURLY_BRACES

                    '}' ->
                        blocksIndicators
                            .removeAllElementsFromLast(CURLY_BRACES)
                            .let { if (!it) onRogueClosingChar('}', this.size, currentBlock.length) }

                    '[' -> blocksIndicators += SQUARE_BRACKETS

                    ']' ->
                        blocksIndicators
                            .removeAllElementsFromLast(SQUARE_BRACKETS)
                            .let { if (!it) onRogueClosingChar(']', this.size, currentBlock.length) }

                    '(' -> blocksIndicators += PARENTHESES

                    ')' ->
                        blocksIndicators
                            .removeAllElementsFromLast(PARENTHESES)
                            .let { if (!it) onRogueClosingChar(')', this.size, currentBlock.length) }

                    '<' -> blocksIndicators += ANGULAR_BRACKETS

                    '>' ->
                        blocksIndicators
                            .removeAllElementsFromLast(ANGULAR_BRACKETS)
                            .let { if (!it) onRogueClosingChar('>', this.size, currentBlock.length) }

                    '"' -> if (!blocksIndicators.removeAllElementsFromLast(DOUBLE_QUOTES)) {
                        blocksIndicators += DOUBLE_QUOTES
                    }

                    '\'' -> if (blocksIndicators.removeAllElementsFromLast(SINGLE_QUOTES)) {
                        blocksIndicators += SINGLE_QUOTES
                    }

                    // TODO: issue #11: html tags
                }
            }
            if (isDone() || !currentBlock.all { it.isSplitter() } || !char.isSplitter()) {
                currentBlock += char
            }
        }

        add(currentBlock)
    }

    val trimmedArguments = arguments.mapIndexed { i, it ->
        when (i) {
            // last argument will be kept as is, removing one "splitting" space if it starts with one
            arguments.lastIndex ->
                if (it.first().isSplitter() && it.firstOrNull() != '\n') {
                    it.drop(1)
                } else {
                    it
                }

            else -> // other arguments will be trimmed. A newline counts as a space
                it.removePrefix("\n").trimStart { it.isSplitter() }
        }
    }

    return trimmedArguments
}

/**
 * Decodes something like `[Alias][Foo]` to `Foo`
 * But also `{@link Foo#main(String[])}` to `Foo.main`
 */
fun String.decodeCallableTarget(): String =
    trim()
        .removePrefix("[")
        .removeSuffix("]")
        .let {
            // for aliased tags like [Foo][Bar]
            if ("][" in it) {
                it.substringAfter("][")
            } else {
                it
            }
        }.trim()
        .removePrefix("<code>") // for javaDoc
        .removeSuffix("</code>")
        .trim()
        // for javaDoc, attempt to be able to read
        // @include {@link Main#main(String[])} as "Main.main"
        .removePrefix("{") // alternatively for javaDoc
        .removeSuffix("}")
        .removePrefix("@link")
        .trim()
        .replace('#', '.')
        .replace(Regex("""\(.*\)"""), "")
        .trim()

/**
 * Get tag name from the start of some content.
 * Can handle both
 * `  @someTag someContent`
 * and
 * `{@someTag someContent}`
 * and will return "someTag" in these cases.
 */
fun DocContent.getTagNameOrNull(): String? = value.getTagNameOrNull()

/**
 * Get tag name from the start of some content.
 * Can handle both
 * `  @someTag someContent`
 * and
 * `{@someTag someContent}`
 * and will return "someTag" in these cases.
 */
fun String.getTagNameOrNull(): String? =
    takeIf { it.trimStart().startsWith('@') || it.startsWith("{@") }
        ?.trimStart()
        ?.removePrefix("{")
        ?.removePrefix("@")
        ?.takeWhile { !it.isWhitespace() && it != '{' && it != '}' }

/**
 * Split doc content in blocks of content and text belonging to tags.
 * The tag, if present, can be found with optional (up to max 2) leading spaces in the first line of the block.
 * You can get the name with [String.getTagNameOrNull].
 * Splitting takes triple backticks and `{@..}` and `${..}` into account.
 * Block "marks" are ignored if "\" escaped.
 * Can be joint with '\n' to get the original content.
 */
fun DocContent.splitPerBlock(ignoreKDocMarkers: Boolean = false): List<DocContent> {
    val docContent = this@splitPerBlock.value.split('\n')
    return buildList {
        var currentBlock = ""

        /**
         * keeps track of the current blocks
         * denoting `{@..}` with [CURLY_BRACES] and triple "`" with [BACKTICKS]
         */
        val blocksIndicators = mutableListOf<Char>()

        fun isInCodeBlock() = BACKTICKS in blocksIndicators

        for (lineToUse in docContent) {
            val lineToCheck = if (ignoreKDocMarkers) {
                lineToUse
                    .trimStart()
                    .removePrefix("*")
                    .removePrefix("/**")
                    .removeSuffix("*/")
                    .removeSuffix(" ")
            } else {
                lineToUse
            }

            // start a new block if the line starts with a tag and we're not
            // in a {@..} or ```..``` block
            val lineStartsWithTag = lineToCheck
                .removePrefix(" ")
                .removePrefix(" ")
                .startsWith("@")

            when {
                // start a new block if the line starts with a tag and we're not in a {@..} or ```..``` block
                lineStartsWithTag && blocksIndicators.isEmpty() -> {
                    if (currentBlock.isNotEmpty()) {
                        this += currentBlock.removeSuffix("\n").asDocContent()
                    }
                    currentBlock = "$lineToUse\n"
                }

                lineToCheck.isEmpty() && blocksIndicators.isEmpty() -> {
                    currentBlock += "\n"
                }

                else -> {
                    if (currentBlock.isEmpty()) {
                        currentBlock = "$lineToUse\n"
                    } else {
                        currentBlock += "$lineToUse\n"
                    }
                }
            }
            var escapeNext = false
            for ((i, char) in lineToCheck.withIndex()) {
                when {
                    escapeNext -> {
                        escapeNext = false
                        continue
                    }

                    char == '\\' ->
                        escapeNext = true

                    // ``` detection
                    char == '`' && lineToCheck.getOrNull(i + 1) == '`' && lineToCheck.getOrNull(i + 2) == '`' ->
                        if (!blocksIndicators.removeAllElementsFromLast(BACKTICKS)) blocksIndicators += BACKTICKS
                }
                if (isInCodeBlock()) continue
                when {
                    // {@ detection
                    char == '{' && lineToCheck.getOrNull(i + 1) == '@' ->
                        blocksIndicators += CURLY_BRACES

                    // ${ detection for ArgDocProcessor
                    char == '{' && lineToCheck.getOrNull(i - 1) == '$' && lineToCheck.getOrNull(i - 2) != '\\' ->
                        blocksIndicators += CURLY_BRACES

                    char == '}' ->
                        blocksIndicators.removeAllElementsFromLast(CURLY_BRACES)
                }
            }
        }
        this += currentBlock.removeSuffix("\n").asDocContent()
    }
}

/**
 * Split doc content in blocks of content and text belonging to tags, with the range of the block.
 * The tag, if present, can be found with optional leading spaces in the first line of the block.
 * You can get the name with [String.getTagNameOrNull].
 * Splitting takes `{}`, `[]`, `()`, and triple backticks into account.
 * Block "marks" are ignored if "\" escaped.
 * Can be joint with '\n' to get the original content.
 */
fun DocContent.splitPerBlockWithRanges(): List<Pair<DocContent, IntRange>> {
    val splitDocContents = this.splitPerBlock()
    var i = 0

    return buildList {
        for ((index, docContent) in splitDocContents.withIndex()) {
            val range =
                if (index == splitDocContents.lastIndex) {
                    i..<i + docContent.value.length // last element has no trailing \n
                } else {
                    i..i + docContent.value.length
                }
            this += Pair(docContent, range)
            i += docContent.value.length + 1
        }
    }
}

/**
 * Finds all inline tag names, including nested ones,
 * together with their respective range in the doc.
 * The list is sorted by depth, with the deepest tags first and then by order of appearance.
 * "{@}" marks are ignored if "\" escaped.
 */
fun DocContent.findInlineTagNamesWithRanges(): List<Pair<String, IntRange>> {
    val text = value
    val map: SortedMap<Int, MutableList<Pair<String, IntRange>>> = sortedMapOf(Comparator.reverseOrder())

    // holds the current start indices of {@tags found
    val queue = ArrayDeque<Int>()

    var escapeNext = false
    for ((i, char) in value.withIndex()) {
        when {
            escapeNext -> escapeNext = false

            char == '\\' -> escapeNext = true

            char == '{' && value.getOrElse(i + 1) { ' ' } == '@' -> {
                queue.addLast(i)
            }

            char == '}' -> {
                if (queue.isNotEmpty()) {
                    val start = queue.removeLast()
                    val end = i
                    val depth = queue.size
                    val tag = text.substring(start..end)
                    val tagName = tag.getTagNameOrNull()

                    if (tagName != null) {
                        map.getOrPut(depth) { mutableListOf() } += tagName to start..end
                    }
                }
            }
        }
    }
    return map.values.flatten()
}

/**
 * Finds all inline tag names, including nested ones.
 * "{@}" marks are ignored if "\" escaped.
 */
fun DocContent.findInlineTagNames(): List<String> = findInlineTagNamesWithRanges().map { it.first }

/** Finds all block tag names. */
fun DocContent.findBlockTagNames(): List<String> =
    splitPerBlock()
        .filter { it.value.trimStart().startsWith("@") }
        .mapNotNull { it.getTagNameOrNull() }

/** Finds all block tags with ranges. */
fun DocContent.findBlockTagsWithRanges(): List<Pair<String, IntRange>> =
    splitPerBlockWithRanges()
        .filter { it.first.value.trimStart().startsWith("@") }
        .mapNotNull {
            val tagName = it.first.getTagNameOrNull() ?: return@mapNotNull null
            tagName to it.second
        }

/** Finds all tag names, including inline and block tags. */
fun DocContent.findTagNames(): List<String> =
    findInlineTagNames() +
        findBlockTagNames()

/** Is able to find an entire JavaDoc/KDoc comment including the starting indent. */
val docRegex = Regex("""( *)/\*\*([^*]|\*(?!/))*?\*/""")

val javaLinkRegex = Regex("""\{@link.*}""")

private enum class ReferenceState {
    NONE,
    INSIDE_REFERENCE,
    INSIDE_ALIASED_REFERENCE,
}

/**
 * Replace KDoc links in doc content with the result of [process].
 *
 * Replaces all `[Aliased][ReferenceLinks]` with `[Aliased][ProcessedPath]`
 * and all `[ReferenceLinks]` with `[ReferenceLinks][ProcessedPath]`.
 */
fun DocContent.replaceKdocLinks(process: (String) -> String): DocContent {
    val kdoc = this.value
    var escapeNext = false
    var insideCodeBlock = false
    var referenceState = NONE

    return buildString {
        var currentBlock = ""

        fun appendCurrentBlock() {
            append(currentBlock)
            currentBlock = ""
        }

        for ((i, char) in kdoc.withIndex()) {
            fun nextChar(): Char? = kdoc.getOrNull(i + 1)

            fun previousChar(): Char? = kdoc.getOrNull(i - 1)

            if (escapeNext) {
                escapeNext = false
            } else {
                when (char) {
                    '\\' -> escapeNext = true

                    '`' -> insideCodeBlock = !insideCodeBlock

                    '[' -> if (!insideCodeBlock) {
                        referenceState =
                            if (previousChar() == ']') {
                                INSIDE_ALIASED_REFERENCE
                            } else {
                                INSIDE_REFERENCE
                            }
                        appendCurrentBlock()
                    }

                    ']' -> if (!insideCodeBlock && nextChar() !in listOf('[', '(')) {
                        currentBlock = processReference(
                            referenceState = referenceState,
                            currentBlock = currentBlock,
                            process = process,
                        )
                        appendCurrentBlock()
                        referenceState = NONE
                    }
                }
            }
            currentBlock += char
        }
        appendCurrentBlock()
    }.asDocContent()
}

private fun StringBuilder.processReference(
    referenceState: ReferenceState,
    currentBlock: String,
    process: (String) -> String,
): String {
    var currentReferenceBlock = currentBlock
    when (referenceState) {
        INSIDE_REFERENCE -> {
            val originalRef = currentReferenceBlock.removePrefix("[")
            if (originalRef.startsWith('`') && originalRef.endsWith('`') || ' ' !in originalRef) {
                val processedRef = process(originalRef)
                if (processedRef == originalRef) {
                    append("[$originalRef")
                } else {
                    append("[$originalRef][$processedRef")
                }
                currentReferenceBlock = ""
            }
        }

        INSIDE_ALIASED_REFERENCE -> {
            val originalRef = currentReferenceBlock.removePrefix("[")
            if (originalRef.startsWith('`') && originalRef.endsWith('`') || ' ' !in originalRef) {
                val processedRef = process(originalRef)
                append("[$processedRef")
                currentReferenceBlock = ""
            }
        }

        NONE -> Unit
    }
    return currentReferenceBlock
}

/**
 * Finds and removes the last occurrence of [element] from the list and, if found, all elements after it.
 * Returns true if [element] was found and removed, false otherwise.
 */
fun <T> MutableList<T>.removeAllElementsFromLast(element: T): Boolean {
    val index = lastIndexOf(element)
    if (index == -1) return false
    val indicesToRemove = index..lastIndex
    for (i in indicesToRemove.reversed()) {
        removeAt(i)
    }
    return true
}

fun IntRange.coerceIn(start: Int = Int.MIN_VALUE, endInclusive: Int = Int.MAX_VALUE) =
    first.coerceAtLeast(start)..last.coerceAtMost(endInclusive)

fun DocContent.removeKotlinLinks(): DocContent =
    buildString {
        val kdoc = this@removeKotlinLinks.value
        var escapeNext = false
        var insideCodeBlock = false
        var referenceState = NONE

        var currentBlock = ""

        fun appendBlock() {
            append(currentBlock)
            currentBlock = ""
        }

        for ((i, char) in kdoc.withIndex()) {
            fun nextChar(): Char? = kdoc.getOrNull(i + 1)

            fun previousChar(): Char? = kdoc.getOrNull(i - 1)

            when {
                escapeNext -> {
                    escapeNext = false
                    currentBlock += char
                }

                char == '\\' -> {
                    escapeNext = true
                }

                char == '`' -> {
                    insideCodeBlock = !insideCodeBlock
                    currentBlock += char
                }

                insideCodeBlock -> {
                    currentBlock += char
                }

                char == '[' -> {
                    referenceState = when {
                        previousChar() == ']' -> {
                            when (referenceState) {
                                INSIDE_REFERENCE -> INSIDE_ALIASED_REFERENCE
                                else -> INSIDE_REFERENCE
                            }
                        }

                        else -> {
                            appendBlock()
                            INSIDE_REFERENCE
                        }
                    }
                }

                char == ']' -> {
                    if (nextChar() !in listOf('[', '(') || referenceState == INSIDE_ALIASED_REFERENCE) {
                        referenceState = NONE

                        if (currentBlock.startsWith("**") && currentBlock.endsWith("**")) {
                            val trimmed = currentBlock.removeSurrounding("**")
                            currentBlock = "**`$trimmed`**"
                        } else {
                            currentBlock = "`$currentBlock`"
                        }

                        appendBlock()
                    }
                }

                referenceState == INSIDE_ALIASED_REFERENCE -> {}

                else -> {
                    currentBlock += char
                }
            }
        }
        appendBlock()
    }
        .replace("****", "")
        .replace("``", "")
        .asDocContent()

/**
 * Coerces the start and end of the range to be at most [endInclusive].
 */
fun IntRange.coerceAtMost(endInclusive: Int) = first.coerceAtMost(endInclusive)..last.coerceAtMost(endInclusive)

/**
 * Maps the given range with [mapping] to one or multiple ranges.
 */
fun IntRange.mapToRanges(mapping: (Int) -> Int): List<IntRange> {
    if (isEmpty()) return emptyList()

    val ranges = mutableListOf<IntRange>()
    val iterator = iterator()
    var start = mapping(iterator.next())
    var end = start

    iterator.forEach {
        val mappedNum = mapping(it)
        if (mappedNum != end + 1) {
            ranges.add(start..end)
            start = mappedNum
        }
        end = mappedNum
    }

    ranges.add(start..end) // Add the last range
    return ranges
}
