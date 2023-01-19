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
fun String.getAtSymbolTargetName(target: String): String =
    also { require("@$target" in this) }
        .trim()
        .removePrefix("@$target")
        .trim()
        .removePrefix("[")
        .removePrefix("[") // twice for scalaDoc
        .removeSuffix("]")
        .removeSuffix("]")
        .removePrefix("<code>") // for javaDoc
        .removeSuffix("</code>")
        .trim()

fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) next() else null

/**
 * Expands the include path to the full path.
 * For instance, if the include path is `plugin.Class.Class2` and the parent path is `com.example.plugin.Class`,
 * the result will be `com.example.plugin.Class.Class2`
 */
fun expandInclude(include: String, parent: String): String {
    if (include.isEmpty() && parent.isEmpty()) return ""
    if (include.isEmpty()) return parent
    if (parent.isEmpty()) return include

    val includePath = include.split(".")
    val parentPath = parent.split(".")

    var result = ""
    val includePathIterator = includePath.iterator()
    val parentPathIterator = parentPath.iterator()

    var nextInclude: String? = includePathIterator.nextOrNull()
    var nextParent: String? = parentPathIterator.nextOrNull()

    while (nextInclude != null || nextParent != null) {
        if (nextInclude == nextParent) {
            result += "$nextParent."
            nextInclude = includePathIterator.nextOrNull()
            nextParent = parentPathIterator.nextOrNull()
        } else if (nextParent != null) {
            result += "$nextParent."
            nextParent = parentPathIterator.nextOrNull()
        } else {
            result += "$nextInclude."
            nextInclude = includePathIterator.nextOrNull()
        }
    }

    return result.dropLastWhile { it == '.' }
}