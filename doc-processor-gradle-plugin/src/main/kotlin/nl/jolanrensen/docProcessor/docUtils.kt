package nl.jolanrensen.docProcessor

import java.util.Comparator

/**
 * Just the contents of the comment, without the `*`-stuff.
 */
typealias DocContent = String

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
fun DocContent.toDoc(indent: Int = 0): String =
    this
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

        var escapeNext = false
        for (char in content) {
            when {
                escapeNext -> escapeNext = false

                char == '\\' -> escapeNext = true

                size >= numberOfArguments - 1 -> Unit

                char.isWhitespace() && blocksIndicators.isEmpty() -> {
                    if (currentBlock.isNotBlank()) add(currentBlock)
                    currentBlock = ""
                }

                char == '{' -> blocksIndicators += CURLY_BRACES
                char == '}' -> blocksIndicators.removeAllElementsFromLast(CURLY_BRACES)

                char == '[' -> blocksIndicators += SQUARE_BRACKETS
                char == ']' -> blocksIndicators.removeAllElementsFromLast(SQUARE_BRACKETS)

                char == '(' -> blocksIndicators += PARENTHESES
                char == ')' -> blocksIndicators.removeAllElementsFromLast(PARENTHESES)

                char == '<' -> blocksIndicators += ANGULAR_BRACKETS
                char == '>' -> blocksIndicators.removeAllElementsFromLast(ANGULAR_BRACKETS)

                char == '`' -> if (!blocksIndicators.removeAllElementsFromLast(BACKTICKS)) blocksIndicators += BACKTICKS
                char == '"' -> if (!blocksIndicators.removeAllElementsFromLast(DOUBLE_QUOTES)) blocksIndicators += DOUBLE_QUOTES
                char == '\'' -> if (blocksIndicators.removeAllElementsFromLast(SINGLE_QUOTES)) blocksIndicators += SINGLE_QUOTES

                // TODO html tags
            }
            if (!(currentBlock.isEmpty() && char.isWhitespace()))
                currentBlock += char
        }

        add(currentBlock)
    }
    return arguments
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
 * The tag, if present, can be found with optional leading spaces in the first line of the block.
 * You can get the name with [String.getTagNameOrNull].
 * Splitting takes `{}`, `[]`, `()`, and triple backticks into account.
 * Block "marks" are ignored if "\" escaped.
 * Can be joint with '\n' to get the original content.
 */
fun DocContent.splitDocContentPerBlock(): List<DocContent> {
    val docContent = this@splitDocContentPerBlock.split('\n')
    return buildList {
        var currentBlock = ""
        val blocksIndicators = mutableListOf<Char>()
        for (line in docContent) {

            // start a new block if the line starts with a tag and we're not
            // in a {} or `````` block
            if (line.trimStart().startsWith("@") && blocksIndicators.isEmpty()) {
                if (currentBlock.isNotEmpty()) add(currentBlock.removeSuffix("\n"))
                currentBlock = "$line\n"
            } else if (line.isEmpty() && blocksIndicators.isEmpty()) {
                currentBlock += "\n"
            } else {
                if (currentBlock.isEmpty()) {
                    currentBlock = "$line\n"
                } else {
                    currentBlock += "$line\n"
                }
            }
            var escapeNext = false
            for (char in line) {

                if (escapeNext) {
                    escapeNext = false
                    continue
                }

                when (char) {
                    '\\' -> escapeNext = true

                    '{' -> blocksIndicators += CURLY_BRACES
                    '}' -> blocksIndicators.removeAllElementsFromLast(CURLY_BRACES)

                    '[' -> blocksIndicators += SQUARE_BRACKETS
                    ']' -> blocksIndicators.removeAllElementsFromLast(SQUARE_BRACKETS)

                    '(' -> blocksIndicators += PARENTHESES
                    ')' -> blocksIndicators.removeAllElementsFromLast(PARENTHESES)

                    '<' -> blocksIndicators += ANGULAR_BRACKETS
                    '>' -> blocksIndicators.removeAllElementsFromLast(ANGULAR_BRACKETS)

                    '`' -> if (!blocksIndicators.removeAllElementsFromLast(BACKTICKS)) blocksIndicators += BACKTICKS

                    // TODO html tags
                }
            }
        }
        add(currentBlock.removeSuffix("\n"))
    }
}

/** Finds any inline tag with its depth, preferring the innermost one.
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

// Regex to match [Aliased][ReferenceLinks].
val aliasedLinkRegex = Regex("""(\[[^\[\]]*]\[)([^\[\]]*)(])""")

// Regex to match [ReferenceLinks].
val singleLinkRegex = Regex("""((^|[^]])\[)([^\[\]]*)(]$|][^\[])""")

// Regex to match [ReferenceLinks] [with space] since this breaks singleLinkRegex.
val doubleSingleLinkRegex = Regex("""((^|[^]])\[)([^\[\]]*)(][^\[]\[)([^\[\]]*)(]$|][^\[])""")

val javaLinkRegex = Regex("""\{@link.*}""")

/**
 * Replace KDoc links in doc content with the result of [process].
 *
 * Replaces all `[Aliased][ReferenceLinks]` with `[Aliased][ProcessedPath]`
 * and all `[ReferenceLinks]` with `[ReferenceLinks][ProcessedPath]`.
 */
fun DocContent.replaceKdocLinks(process: (String) -> String): DocContent = this
    .replace(aliasedLinkRegex) { // replace all [Aliased][ReferenceLinks] with [Aliased][ProcessedPath]
        it.groupValues.let {
            buildString {
                append(it[1])
                append(
                    process(it[2])
                )
                append(it[3])
            }
        }
    }
    .replace(doubleSingleLinkRegex) { // replace all [ReferenceLinks] [with space] with [ReferenceLinks][ProcessedPath] [with space][ProcessedPath]
        it.groupValues.let {
            buildString {
                append(it[1])
                append(it[3])
                append("][")
                append(
                    process(it[3])
                )
                append(it[4])
                append(it[5])
                append("][")
                append(
                    process(it[5])
                )
                append(it[6])
            }
        }
    }
    .replace(singleLinkRegex) { // replace all [ReferenceLinks] with [ReferenceLinks][ProcessedPath]
        it.groupValues.let {
            buildString {
                append(it[0].dropLastWhile { it != ']' })
                append("[")
                append(
                    process(it[3])
                )
                append(it[4])
            }
        }
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

// used to keep track of the current blocks
private const val CURLY_BRACES = '{'
private const val SQUARE_BRACKETS = '['
private const val PARENTHESES = '('
private const val ANGULAR_BRACKETS = '<'
private const val BACKTICKS = '`'
private const val DOUBLE_QUOTES = '"'
private const val SINGLE_QUOTES = '\''