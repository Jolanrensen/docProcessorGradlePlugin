package nl.jolanrensen.docProcessor

/**
 * Returns the actual content of the KDoc/Javadoc comment
 */
fun String.getDocContent() = this
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
 * Turns multi-line String into valid KDoc/Javadoc.
 */
fun String.toDoc(indent: Int = 0) =
    this
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

/**
 * Get @tag target name.
 * For instance, changes `@include [Foo]` to `Foo`
 */
fun String.getTagTarget(tag: String): String =
    also { require("@$tag" in this) { "Could not find @$tag in $this" } }
        .replace("\n", "")
        .trim()
        .removePrefix("{") // for inner tags
        .removeSuffix("}")

        .removePrefix("@$tag")
        .trim()
        .removePrefix("[")
        .removePrefix("[") // twice for scalaDoc
        .removeSuffix("]")
        .removeSuffix("]")

        .removePrefix("<code>") // for javaDoc
        .removeSuffix("</code>")

        // for javaDoc, attempt to be able to read
        // @include {@link Main#main(String[])} as "Main.main"
        .removePrefix("{") // alternatively for javaDoc
        .removeSuffix("}")
        .trim()
        .removePrefix("@link ")
        .replace('#', '.')
        .replace(Regex("""\(.*\)"""), "")
        .trim()

/**
 * Get file target.
 * For instance, changes `@file (./something.md)` to `./something.md`
 */
fun String.getFileTarget(tag: String): String =
    also { require("@$tag" in this) }
        .replace("\n", "")
        .trim()
        .removePrefix("{") // for inner tags
        .removeSuffix("}")

        .removePrefix("@$tag")
        .trim()

        // TODO figure out how to make file location clickable
        // TODO both for Java and Kotlin
        .removePrefix("(")
        .removeSuffix(")")
        .trim()

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
        ?.takeWhile { !it.isWhitespace() }

fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) next() else null

/**
 * Expands the target path to the full path.
 * For instance, if the target path is `plugin.Class.Class2` and the parent path is `com.example.plugin.Class`,
 * the result will be `com.example.plugin.Class.Class2`
 */
fun String.expandPath(currentFullPath: String): String {
    val parent = currentFullPath.take(currentFullPath.lastIndexOf('.').coerceAtLeast(0))
    if (isEmpty() && parent.isEmpty()) return ""
    if (isEmpty()) return parent
    if (parent.isEmpty()) return this

    val targetPath = split(".")
    val parentPath = parent.split(".")

    var result = ""
    val targetPathIterator = targetPath.iterator()
    val parentPathIterator = parentPath.iterator()

    var nextTarget: String? = targetPathIterator.nextOrNull()
    var nextParent: String? = parentPathIterator.nextOrNull()

    while (nextTarget != null || nextParent != null) {
        if (nextTarget == nextParent) {
            result += "$nextParent."
            nextTarget = targetPathIterator.nextOrNull()
            nextParent = parentPathIterator.nextOrNull()
        } else if (nextParent != null) {
            result += "$nextParent."
            nextParent = parentPathIterator.nextOrNull()
        } else {
            result += "$nextTarget."
            nextTarget = targetPathIterator.nextOrNull()
        }
    }

    return result.dropLastWhile { it == '.' }
}

/**
 * Split doc content in blocks of content and text belonging to tags.
 * The tag, if present, can be found with optional leading spaces in the first line of the block.
 * You can get the name with [String.getTagNameOrNull].
 * Splitting takes `{}`, `[]`, `()`, and triple backticks into account.
 * Can be joint with '\n' to get the original content.
 */
fun String.splitDocContent(): List<String> = buildList {
    val docContent = this@splitDocContent.split('\n')

    var currentBlock = ""
    val blocksIndicators = mutableListOf<String>()
    for (line in docContent) {

        // start a new block if the line starts with a tag and we're not
        // in a {} or `````` block
        if (line.trimStart().startsWith("@") && blocksIndicators.isEmpty()) {
            add(currentBlock)
            currentBlock = line
        } else {
            if (currentBlock.isEmpty()) {
                currentBlock = line
            } else {
                currentBlock += "\n$line"
            }
        }
        for (char in line) when (char) {
            '{' -> blocksIndicators += "{}"
            '}' -> blocksIndicators -= "{}"

            '[' -> blocksIndicators += "[]"
            ']' -> blocksIndicators -= "[]"

            '(' -> blocksIndicators += "()"
            ')' -> blocksIndicators -= "()"
        }
        val numberOfBackTicks = line.count { it == '`' } / 3
        repeat(numberOfBackTicks) {
            if ("```" in blocksIndicators)
                blocksIndicators -= "```"
            else
                blocksIndicators += "```"
        }
    }
    add(currentBlock)
}

/**
 * Split doc content in parts being either an inner tag or not.
 * For instance, it splits `Hi there {@tag some {@test}}` into `["Hi there ", "{@tag some {@test}}"]`
 * Tags inside single and triple backticks are ignored.
 * You can get the name with [String.getTagNameOrNull].
 * Can be joint with '' to get the original content.
 */
fun String.splitDocContentOnInnerTags(): List<String> = buildList {
    val docContent = this@splitDocContentOnInnerTags

    var currentBlock = ""
    val blocksIndicators = mutableListOf<String>()
    for (char in docContent) {
        when (char) {
            '{' -> {
                if (blocksIndicators.isEmpty()) {
                    add(currentBlock)
                    currentBlock = ""
                }
                currentBlock += char
                blocksIndicators += "{}"
            }

            '}' -> {
                currentBlock += char
                blocksIndicators -= "{}"
                if (blocksIndicators.isEmpty()) {
                    add(currentBlock)
                    currentBlock = ""
                }
            }

            '`' -> {
                if ("``" in blocksIndicators)
                    blocksIndicators -= "``"
                else
                    blocksIndicators += "``"
                currentBlock += char
            }

            else -> currentBlock += char
        }
    }
    add(currentBlock)
}

fun String.findInnerTagsInDocContent(): List<String> =
    splitDocContentOnInnerTags()
        .filter { it.startsWith("{@") }
        .mapNotNull { it.getTagNameOrNull() }

fun String.findNormalTagsInDocContent(): List<String> =
    splitDocContent()
        .filter { it.trimStart().startsWith("@") }
        .mapNotNull { it.getTagNameOrNull() }

fun String.findTagsInDocContent(): List<String> =
    findInnerTagsInDocContent() + findNormalTagsInDocContent()

/**
 * Is able to find an entire JavaDoc/KDoc comment including the starting indent.
 */
val docRegex = Regex("""( *)/\*\*([^*]|\*(?!/))*?\*/""")

/**
 * Is able to find JavaDoc/KDoc tags without content.
 * Tags can be at the beginning of a line or after a `{`, like in `{@see String}`
 */
val tagRegex = Regex("""(^|\n|\{) *@[a-zA-Z][a-zA-Z0-9]*""")