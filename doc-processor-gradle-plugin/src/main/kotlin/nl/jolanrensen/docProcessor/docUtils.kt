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
 * Get include target name.
 * For instance, changes `@include [Foo]` to `Foo`
 */
fun String.getTagTarget(tag: String): String =
    also { require("@$tag" in this) }
        .replace("\n", "")
        .trim()
        .removePrefix("@$tag")
        .trim()
        .removePrefix("[")
        .removePrefix("[") // twice for scalaDoc
        .removeSuffix("]")
        .removeSuffix("]")
        .removePrefix("<code>") // for javaDoc
        .removeSuffix("</code>")
        .trim()

fun String.getTagNameOrNull(): String? =
    takeIf { it.startsWith('@') }
        ?.removePrefix("@")
        ?.split(' ')
        ?.firstOrNull()

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

fun String.splitDocContent(): List<String> = buildList {
    val docContent = this@splitDocContent.split('\n')

    var currentLine = ""
    for (line in docContent) {
        if (line.startsWith("@")) {
            add(currentLine)
            currentLine = line
        } else {
            if (currentLine.isEmpty())
                currentLine = line
            else
                currentLine += "\n$line"
        }
    }
    add(currentLine)
}

//fun main() {
//    val text = """
//        /**
//         * Hello World!
//         * @tag something
//         * something @tag
//         * @tag b B
//         * @tag c C
//         */
//    """.trimIndent().getDocContent()
//
//    println(
//        text.splitDocContent()
//            .joinToString("\n")
////            .joinToString(prefix = "\"", postfix = "\"", separator = "\", \"")
//    )
//}