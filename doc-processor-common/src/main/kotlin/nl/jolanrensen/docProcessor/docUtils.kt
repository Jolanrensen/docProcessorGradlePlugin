package nl.jolanrensen.docProcessor

import nl.jolanrensen.docProcessor.ReferenceState.*
import java.util.Comparator

/**
 * Just the contents of the comment, without the `*`-stuff.
 */
typealias DocContent = String

// used to keep track of the current blocks
private const val CURLY_BRACES = '{'
private const val SQUARE_BRACKETS = '['
private const val PARENTHESES = '('
private const val ANGULAR_BRACKETS = '<'
private const val BACKTICKS = '`'
private const val DOUBLE_QUOTES = '"'
private const val SINGLE_QUOTES = '\''

/**
 * Returns the actual content of the KDoc/Javadoc comment
 */
fun String.getDocContentOrNull(): DocContent? {
    if (isBlank() || !startsWith("/**") || !endsWith("*/")) return null

    val lines = split('\n').withIndex()

    val result = lines.joinToString("\n") { (i, it) ->
        var line = it

        if (i == 0) {
            line = line.trimStart().removePrefix("/**")
        }
        if (i == lines.count() - 1) {
            val lastLine = line.trimStart()

            line = if (lastLine == "*/") {
                ""
            } else {
                lastLine
                    .removePrefix("*")
                    .removeSuffix("*/")
                    .removeSuffix(" ") // optional extra space at the end
            }
        }
        if (i != 0 && i != lines.count() - 1) {
            line = line.trimStart().removePrefix("*")
        }

        line = line.removePrefix(" ") // optional extra space at the start

        line
    }

    return result
}

/**
 * Turns multi-line String into valid KDoc/Javadoc.
 */
fun DocContent.toDoc(indent: Int = 0): String = this
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
    }

/**
 * Can retrieve the arguments of an inline- or block-tag.
 * Arguments are split by spaces, unless they are in a block of "{}", "[]", "()", "<>", "`", """, or "'".
 * Blocks "marks" are ignored if "\" escaped.
 */
fun String.getTagArguments(tag: String, numberOfArguments: Int): List<String> {
    require("@$tag" in this) { "Could not find @$tag in $this" }
    require(numberOfArguments > 0) { "numberOfArguments must be greater than 0" }

    var content = this

    // remove inline tag stuff
    if (content.startsWith("{") && content.endsWith("}")) {
        content = content.removePrefix("{").removeSuffix("}")
    }

    // remove leading spaces
    content = content.trimStart()

    // remove tag
    content = content.removePrefix("@$tag").trimStart()

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
                    if (currentBlock.isNotBlank()) add(currentBlock)
                    currentBlock = ""
                }

                char == '`' -> if (!blocksIndicators.removeAllElementsFromLast(BACKTICKS)) blocksIndicators += BACKTICKS
            }
            if (!isInCodeBlock()) when (char) {
                '{' -> blocksIndicators += CURLY_BRACES
                '}' -> blocksIndicators.removeAllElementsFromLast(CURLY_BRACES)
                '[' -> blocksIndicators += SQUARE_BRACKETS
                ']' -> blocksIndicators.removeAllElementsFromLast(SQUARE_BRACKETS)
                '(' -> blocksIndicators += PARENTHESES
                ')' -> blocksIndicators.removeAllElementsFromLast(PARENTHESES)
                '<' -> blocksIndicators += ANGULAR_BRACKETS
                '>' -> blocksIndicators.removeAllElementsFromLast(ANGULAR_BRACKETS)
                '"' -> if (!blocksIndicators.removeAllElementsFromLast(DOUBLE_QUOTES)) blocksIndicators += DOUBLE_QUOTES
                '\'' -> if (blocksIndicators.removeAllElementsFromLast(SINGLE_QUOTES)) blocksIndicators += SINGLE_QUOTES

                // TODO: issue #11: html tags
            }
            if (isDone() || !(currentBlock.isEmpty() && char.isWhitespace()))
                currentBlock += char
        }

        add(currentBlock)
    }

    val trimmedArguments = arguments.mapIndexed { i, it ->
        when (i) {
            arguments.lastIndex -> // last argument will be kept as is, removing one "splitting" space if it starts with one
                if (it.startsWith(" ") || it.startsWith("\t")) it.drop(1)
                else it

            else -> // other arguments will be trimmed. A newline counts as a space
                it.removePrefix("\n").trimStart(' ', '\t')
        }
    }

    return trimmedArguments
}

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
                escapeNext -> escapeNext = false

                char == '\\' -> escapeNext = true

                isDone() -> Unit

                char.isSplitter() && blocksIndicators.isEmpty() -> {
                    if (!currentBlock.all { it.isSplitter() }) add(currentBlock)
                    currentBlock = ""
                }

                char == '`' -> if (!blocksIndicators.removeAllElementsFromLast(BACKTICKS)) blocksIndicators += BACKTICKS
            }
            if (!isInCodeBlock()) when (char) {
                '{' -> blocksIndicators += CURLY_BRACES
                '}' -> blocksIndicators.removeAllElementsFromLast(CURLY_BRACES)
                    .let { if (!it) onRogueClosingChar('}', this.size, currentBlock.length) }

                '[' -> blocksIndicators += SQUARE_BRACKETS
                ']' -> blocksIndicators.removeAllElementsFromLast(SQUARE_BRACKETS)
                    .let { if (!it) onRogueClosingChar(']', this.size, currentBlock.length) }

                '(' -> blocksIndicators += PARENTHESES
                ')' -> blocksIndicators.removeAllElementsFromLast(PARENTHESES)
                    .let { if (!it) onRogueClosingChar(')', this.size, currentBlock.length) }

                '<' -> blocksIndicators += ANGULAR_BRACKETS
                '>' -> blocksIndicators.removeAllElementsFromLast(ANGULAR_BRACKETS)
                    .let { if (!it) onRogueClosingChar('>', this.size, currentBlock.length) }

                '"' -> if (!blocksIndicators.removeAllElementsFromLast(DOUBLE_QUOTES)) blocksIndicators += DOUBLE_QUOTES
                '\'' -> if (blocksIndicators.removeAllElementsFromLast(SINGLE_QUOTES)) blocksIndicators += SINGLE_QUOTES

                // TODO: issue #11: html tags
            }
            if (isDone() || !currentBlock.all { it.isSplitter() } || !char.isSplitter())
                currentBlock += char
        }

        add(currentBlock)
    }

    val trimmedArguments = arguments.mapIndexed { i, it ->
        when (i) {
            arguments.lastIndex -> // last argument will be kept as is, removing one "splitting" space if it starts with one
                if (it.first().isSplitter() && it.firstOrNull() != '\n') it.drop(1)
                else it

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

        .let { // for aliased tags like [Foo][Bar]
            if ("][" in it) it.substringAfter("][")
            else it
        }
        .trim()

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
fun DocContent.getTagNameOrNull(): String? =
    takeIf { it.trimStart().startsWith('@') || it.startsWith("{@") }
        ?.trimStart()
        ?.removePrefix("{")
        ?.removePrefix("@")
        ?.takeWhile { !it.isWhitespace() }

/**
 * Split doc content in blocks of content and text belonging to tags.
 * The tag, if present, can be found with optional (up to max 2) leading spaces in the first line of the block.
 * You can get the name with [String.getTagNameOrNull].
 * Splitting takes triple backticks and `{@..}` into account.
 * Block "marks" are ignored if "\" escaped.
 * Can be joint with '\n' to get the original content.
 */
fun DocContent.splitDocContentPerBlock(): List<DocContent> { // TODO remove blocks as they are inconsistent with kdoc spec
    val docContent = this@splitDocContentPerBlock.split('\n')
    return buildList {
        var currentBlock = ""

        /**
         * keeps track of the current blocks
         * denoting `{@..}` with [CURLY_BRACES] and triple "`" with [BACKTICKS]
         */
        val blocksIndicators = mutableListOf<Char>()

        fun isInCodeBlock() = BACKTICKS in blocksIndicators

        for (line in docContent) {

            // start a new block if the line starts with a tag and we're not
            // in a {@..} or ```..``` block
            val lineStartsWithTag = line
                .removePrefix(" ")
                .removePrefix(" ")
                .startsWith("@")

            when {
                // start a new block if the line starts with a tag and we're not in a {@..} or ```..``` block
                lineStartsWithTag && blocksIndicators.isEmpty() -> {
                    if (currentBlock.isNotEmpty())
                        this += currentBlock.removeSuffix("\n")
                    currentBlock = "$line\n"
                }

                line.isEmpty() && blocksIndicators.isEmpty() -> {
                    currentBlock += "\n"
                }

                else -> {
                    if (currentBlock.isEmpty()) {
                        currentBlock = "$line\n"
                    } else {
                        currentBlock += "$line\n"
                    }
                }
            }
            var escapeNext = false
            for ((i, char) in line.withIndex()) {
                when {
                    escapeNext -> {
                        escapeNext = false
                        continue
                    }

                    char == '\\' ->
                        escapeNext = true

                    // ``` detection
                    char == '`' && line.getOrNull(i + 1) == '`' && line.getOrNull(i + 2) == '`' ->
                        if (!blocksIndicators.removeAllElementsFromLast(BACKTICKS)) blocksIndicators += BACKTICKS
                }
                if (isInCodeBlock()) continue
                when {
                    char == '{' && line.getOrNull(i + 1) == '@' ->
                        blocksIndicators += CURLY_BRACES

                    char == '}' ->
                        blocksIndicators.removeAllElementsFromLast(CURLY_BRACES)
                }
            }
        }
        add(currentBlock.removeSuffix("\n"))
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
fun DocContent.splitDocContentPerBlockWithRanges(): List<Pair<DocContent, IntRange>> {
    val splitDocContents = this.splitDocContentPerBlock()
    var i = 0

    return buildList {
        for (docContent in splitDocContents) {
            add(Pair(docContent, i..i + docContent.length))
            i += docContent.length + 1
        }
    }
}

/**
 * Finds any inline {@tag ...} with its depth, preferring the innermost one.
 * "{@}" marks are ignored if "\" escaped.
 */
private fun DocContent.findInlineTagRangesWithDepthOrNull(): Pair<IntRange, Int>? {
    var depth = 0
    var start: Int? = null
    var escapeNext = false
    for ((i, char) in this.withIndex()) {
        // escape this char
        when {
            escapeNext -> escapeNext = false

            char == '\\' -> escapeNext = true

            char == '{' && this.getOrNull(i + 1) == '@' -> {
                start = i
                depth++
            }

            char == '}' -> {
                if (start != null) {
                    return Pair(start..i, depth)
                }
            }
        }
    }
    return null
}

/**
 * Finds all inline tag names, including nested ones,
 * together with their respective range in the doc.
 * The list is sorted by depth, with the deepest tags first and then by order of appearance.
 * "{@}" marks are ignored if "\" escaped.
 */
fun DocContent.findInlineTagNamesInDocContentWithRanges(): List<Pair<String, IntRange>> {
    var text = this

    return buildMap<Int, MutableList<Pair<String, IntRange>>> {
        while (text.findInlineTagRangesWithDepthOrNull() != null) {
            val (range, depth) = text.findInlineTagRangesWithDepthOrNull()!!
            val comment = text.substring(range)
            comment.getTagNameOrNull()?.let { tagName ->
                getOrPut(depth) { mutableListOf() } += Pair(tagName, range)
            }

            text = text.replaceRange(
                range = range,
                replacement = comment
                    .replace('{', '<')
                    .replace('}', '>'),
            )
        }
    }.toSortedMap(Comparator.reverseOrder())
        .flatMap { it.value }
}

/**
 * Finds all inline tag names, including nested ones.
 * "{@}" marks are ignored if "\" escaped.
 */
fun DocContent.findInlineTagNamesInDocContent(): List<String> =
    findInlineTagNamesInDocContentWithRanges().map { it.first }

/** Finds all block tag names. */
fun DocContent.findBlockTagNamesInDocContent(): List<String> =
    splitDocContentPerBlock()
        .filter { it.trimStart().startsWith("@") }
        .mapNotNull { it.getTagNameOrNull() }

/** Finds all tag names, including inline and block tags. */
fun DocContent.findTagNamesInDocContent(): List<String> =
    findInlineTagNamesInDocContent() + findBlockTagNamesInDocContent()


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
    val kdoc = this
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
    }
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
 * Finds removes the last occurrence of [element] from the list and, if found, all elements after it.
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
