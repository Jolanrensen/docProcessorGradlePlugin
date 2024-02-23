package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.*
import nl.jolanrensen.docProcessor.ProgrammingLanguage.JAVA
import java.util.*

/**
 * @see ArgDocProcessor
 */
const val ARG_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.ArgDocProcessor"

/**
 * [Boolean] argument controlling whether to log warnings when an argument is not found.
 * Default is `true`.
 */
const val ARG_DOC_PROCESSOR_LOG_NOT_FOUND = "$ARG_DOC_PROCESSOR.LOG_NOT_FOUND"

/**
 * Adds two tags to your arsenal:
 * - `@set key some content` / `{@set key some content}`
 *   to declare a variable that can be used in the same doc.
 * - `@get key default` / `{@get key default}`
 *   to read a variable from the same doc (with an optional default for if no variable with that key is defined).
 *    - `$key` / `${key}` can also be used instead of `{@get key}`.
 *    - `$key=default` / `${key=default}` can also be used instead of `{@get key default}`.
 *
 * This can be useful to repeat the same content in multiple places in the same doc, but
 * more importantly, it can be used in conjunction with [IncludeDocProcessor].
 *
 * All `@set` tags are processed before `@get` tags.
 *
 * For example:
 *
 * File A:
 * ```kotlin
 * /** NOTE: The {@get operation} operation is part of the public API. */
 *  internal interface ApiNote
 * ```
 *
 * File B:
 *```kotlin
 * /**
 *  * Some docs
 *  * @include [ApiNote]
 *  * @set operation update
 *  */
 * fun update() {}
 * ```
 *
 * File B after processing:
 * ```kotlin
 * /**
 *  * Some docs
 *  * NOTE: The update operation is part of the public API.
 *  */
 * fun update() {}
 * ```
 *
 * NOTE: If there are multiple `@set` tags with the same name, the last one processed will be used.
 * The order is: Inline tags: depth-first, left-to-right. Block tags: top-to-bottom.
 *
 * NOTE: Use `[References]` as keys if you want extra refactoring-safety.
 * They are queried and saved by their fully qualified name.
 */
class ArgDocProcessor : TagDocProcessor() {

    internal companion object {
        internal val OLD_RETRIEVE_ARGUMENT_TAGS = listOf(
            "includeArg",
            "getArg",
        )

        internal val OLD_DECLARE_ARGUMENT_TAGS = listOf(
            "arg",
            "setArg",
        )

        internal val RETRIEVE_ARGUMENT_TAGS = listOf(
            "get",
            // $, handled in process()
        ) + OLD_RETRIEVE_ARGUMENT_TAGS

        internal val DECLARE_ARGUMENT_TAGS = listOf(
            "set",
        ) + OLD_DECLARE_ARGUMENT_TAGS
    }

    private val supportedTags = listOf(
        RETRIEVE_ARGUMENT_TAGS,
        DECLARE_ARGUMENT_TAGS,
    ).flatten()

    override fun tagIsSupported(tag: String): Boolean = tag in supportedTags

    data class DocWrapperWithArgMap(
        val doc: DocumentableWrapper,
        val args: MutableMap<String, String> = mutableMapOf(),
    )

    data class DocWrapperWithArgSet(
        val doc: DocumentableWrapper,
        val args: MutableSet<String> = mutableSetOf(),
    )

    override fun shouldContinue(
        i: Int,
        anyModifications: Boolean,
        processLimit: Int,
    ): Boolean {
        val processLimitReached = i >= processLimit
        if (processLimitReached)
            onProcessError()

        val atLeastOneRun = i > 0

        // We can break out of the recursion if there are no more changes. We don't need to throw an error if an
        // argument is not found, as it might be defined in a different file.
        if (atLeastOneRun && !anyModifications) {
            val log = arguments[ARG_DOC_PROCESSOR_LOG_NOT_FOUND] as? Boolean ?: true
            if (log) {
                val fileTexts = argsNotFound.values
                    .distinctBy { it.doc.file }
                    .associate { it.doc.file to it.doc.file.readText() }

                for ((_, docWithArgs) in argsNotFound) {
                    val (documentable, args) = docWithArgs

                    val (line, char) = fileTexts[documentable.file]!!
                        .getLineAndCharacterOffset(documentable.docFileTextRange.first)

                    if (args.isNotEmpty()) {
                        logger.warn {
                            buildString {
                                val arguments = if (args.size == 1) "argument" else "arguments"
                                appendLine("Could not find @$DECLARE_ARGUMENT_TAGS $arguments in doc (${documentable.file.absolutePath}:$line:$char):")
                                appendLine(args.joinToString(",\n") { "  \"@$RETRIEVE_ARGUMENT_TAGS $it\"" })
                            }
                        }
                    }
                }
            }

            return false
        }

        return super.shouldContinue(i, anyModifications, processLimit)
    }

    // @set map for path -> arg name -> value
    private val argMap: MutableMap<UUID, DocWrapperWithArgMap> = mutableMapOf()

    private val argsNotFound: MutableMap<UUID, DocWrapperWithArgSet> = mutableMapOf()

    /**
     * Preprocess all `${a}` and `$a` tags to `{@get a}`
     * and all `${a=b}` and `$a=b` tags to `{@get a b}`
     * before running the normal tag processor.
     */
    override fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath {
        val mutable = documentablesByPath.toMutable()
        for ((_, docs) in mutable.documentablesToProcess) {
            for (doc in docs) {
                doc.modifyDocContentAndUpdate(
                    doc.docContent.replaceDollarNotation()
                )
            }
        }

        return super.process(processLimit, mutable)
    }

    // TODO remove if deprecation is gone
    private fun provideNewNameWarning(documentable: DocumentableWrapper) =
        logger.warn {
            buildString {
                val (line, char) = documentable.file
                    .readText()
                    .getLineAndCharacterOffset(documentable.docFileTextRange.first)

                appendLine("Old tag names used in doc: (${documentable.file.absolutePath}:$line:$char)")
                appendLine("The tag names \"$OLD_RETRIEVE_ARGUMENT_TAGS\" is deprecated. Use \"${RETRIEVE_ARGUMENT_TAGS.first()}\" or \$ instead.")
                appendLine("The tag name \"$OLD_DECLARE_ARGUMENT_TAGS\" is deprecated. Use \"${DECLARE_ARGUMENT_TAGS.first()}\" instead.")
            }
        }

    private fun processTag(
        tagWithContent: String,
        documentable: DocumentableWrapper,
    ): String {
        val unfilteredDocumentablesByPath by lazy { documentablesByPath.withoutFilters() }
        val tagName = tagWithContent.getTagNameOrNull()
        val isDeclareArgumentDeclaration = tagName in DECLARE_ARGUMENT_TAGS

        val tagNames = documentable.docContent.findTagNamesInDocContent()

        val declareArgTagsStillPresent = DECLARE_ARGUMENT_TAGS.any { it in tagNames }
        val retrieveArgTagsPresent = RETRIEVE_ARGUMENT_TAGS.any { it in tagNames }

        if (tagName in OLD_RETRIEVE_ARGUMENT_TAGS || tagName in OLD_DECLARE_ARGUMENT_TAGS)
            provideNewNameWarning(documentable)

        return when {
            isDeclareArgumentDeclaration -> { // @set
                val argArguments =
                    tagWithContent.getTagArguments(
                        tag = tagName!!,
                        numberOfArguments = 2,
                    )

                val originalKey = argArguments.first()
                var keys = listOf(originalKey)

                if (originalKey.startsWith('[') && originalKey.contains(']')) {
                    keys = buildReferenceKeys(originalKey, documentable, unfilteredDocumentablesByPath)
                }

                val value = argArguments.getOrElse(1) { "" }
                    .trimStart()
                    .removeSuffix("\n")

                argMap.getOrPut(documentable.identifier) {
                    DocWrapperWithArgMap(documentable)
                }.also { (_, args) ->
                    for (it in keys) args[it] = value
                }

                argsNotFound.getOrPut(documentable.identifier) {
                    DocWrapperWithArgSet(documentable)
                }.args -= keys.toSet()

                ""
            }

            declareArgTagsStillPresent -> {
                // skip @get tags if there are still @set's present
                tagWithContent
            }

            else -> { // @get
                val includeArgArguments: List<String> = buildList {
                    if (retrieveArgTagsPresent) {
                        this += tagWithContent.getTagArguments(
                            tag = tagName!!,
                            numberOfArguments = 2,
                        )
                    }
                }

                val originalKey = includeArgArguments.first()

                // for stuff written after the @get tag, save and include it later
                val extraContent = includeArgArguments.getOrElse(1) { "" }

                var keys = listOf(originalKey)

                // TODO: issue #8: Expanding Java reference links
                if (documentable.programmingLanguage == JAVA && javaLinkRegex in originalKey) {
                    logger.warn {
                        "Java {@link statements} are not replaced by their fully qualified path. " +
                                "Make sure to use fully qualified paths in {@link statements} when " +
                                "using {@link statements} as a key in @set."
                    }
                }

                if (originalKey.startsWith('[') && originalKey.contains(']')) {
                    keys = buildReferenceKeys(originalKey, documentable, unfilteredDocumentablesByPath)
                }

                val content = keys.firstNotNullOfOrNull { key ->
                    argMap[documentable.identifier]?.args?.get(key)
                }

                if (content == null) {
                    argsNotFound.getOrPut(documentable.identifier) {
                        DocWrapperWithArgSet(documentable)
                    }.args += keys

                    // if there is no content, we return the extra content (default), this can be empty
                    extraContent
                } else {
                    content
                }
            }
        }
    }

    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = processTag(
        tagWithContent = tagWithContent,
        documentable = documentable,
    )

    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = processTag(
        tagWithContent = tagWithContent,
        documentable = documentable,
    )

    private fun buildReferenceKeys(
        originalKey: String,
        documentable: DocumentableWrapper,
        documentablesNoFilters: DocumentablesByPath,
    ): List<String> {
        var keys = listOf(originalKey)
        val reference = originalKey.decodeCallableTarget()
        val referencedDocumentable = documentable.queryDocumentables(
            query = reference,
            documentablesNoFilters = documentablesNoFilters,
            documentables = documentablesByPath,
        )

        if (referencedDocumentable != null)
            keys = listOfNotNull(
                referencedDocumentable.fullyQualifiedPath,
                referencedDocumentable.fullyQualifiedExtensionPath,
            ).map { "[$it]" }

        return keys
    }
}

/**
 * Replaces all `${KEY}` and `$KEY` with `{@get KEY}`
 * and all `${KEY=DEFAULT}` and `$KEY=DEFAULT` with `{@get KEY DEFAULT}`
 * for some doc's content.
 */
fun DocContent.replaceDollarNotation(): DocContent {
    var text = this

    // First replacing all ${KEY} with {@get KEY}
    // and ${KEY=DEFAULT} with {@get KEY DEFAULT}
    text = text.`replace ${}'s`()

    // Then replacing all "$KEY test" with "{@get KEY} test"
    // and "$KEY=DEFAULT" with "{@get KEY DEFAULT}"
    text = text.`replace $tags`()

    return text
}

/**
 * Replaces all `${KEY}` with `{@get KEY}`
 * and `${KEY=DEFAULT}` with `{@set KEY DEFAULT}`
 */
fun DocContent.`replace ${}'s`(): DocContent {
    val text = this
    val locations = `find ${}'s`()
    val locationsWithKeyValues = locations
        .associateWith { text.substring(it).findKeyAndValueFromDollarSign() }

    val nonOverlappingRangesWithReplacement = locationsWithKeyValues
        .flatMap { (range, keyAndValue) ->
            val (key, value) = keyAndValue
            buildList {
                // replacing "${" with "{@get "
                this += range.first..range.first + 1 to "{@${ArgDocProcessor.RETRIEVE_ARGUMENT_TAGS.first()} "
                if (value != null) {
                    val equalsPosition = range.first + 2 + key.length

                    // replacing "=" with " "
                    this += equalsPosition..equalsPosition to " "
                }
            }
        }

    return text.replaceNonOverlappingRanges(*nonOverlappingRangesWithReplacement.toTypedArray())
}

/**
 * Finds all inline ${} tags, including nested ones
 * by their respective range in the doc.
 * The list is sorted by depth, with the deepest tags first and then by order of appearance.
 * "${}" marks are ignored if "\" escaped.
 */
private fun DocContent.`find ${}'s`(): List<IntRange> {
    var text = this

    /*
     * Finds any inline ${...} with its depth, preferring the innermost one.
     * "${}" marks are ignored if "\" escaped.
     */
    fun DocContent.findInlineDollarTagRangesWithDepthOrNull(): Pair<IntRange, Int>? {
        var depth = 0
        var start: Int? = null
        var escapeNext = false
        for ((i, char) in this.withIndex()) {
            // escape this char
            when {
                escapeNext -> escapeNext = false

                char == '\\' -> escapeNext = true

                char == '$' && this.getOrNull(i + 1) == '{' -> {
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

    return buildMap<Int, MutableList<IntRange>> {
        while (text.findInlineDollarTagRangesWithDepthOrNull() != null) {
            val (range, depth) = text.findInlineDollarTagRangesWithDepthOrNull()!!
            val comment = text.substring(range)
            getOrPut(depth) { mutableListOf() } += range

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
 * Replaces all "$KEY" with "{@get KEY}"
 * and "$KEY=DEFAULT" with "{@get KEY DEFAULT}"
 */
fun DocContent.`replace $tags`(): DocContent {
    val text = this
    val locations = `find $tags`()

    val nonOverlappingRangesWithReplacement = locations.flatMap { (range, equalsPosition) ->
        buildList {
            // replacing "$" with "{@get "
            this += range.first..range.first to "{@${ArgDocProcessor.RETRIEVE_ARGUMENT_TAGS.first()} "
            if (equalsPosition != null) {
                // replacing "=" with " "
                this += equalsPosition..equalsPosition to " "
            }

            // adding "}" at the end
            this += range.last + 1..range.last to "}"
        }
    }

    return text.replaceNonOverlappingRanges(*nonOverlappingRangesWithReplacement.toTypedArray())
}

/**
 * Finds all inline $ tags by their respective range in the doc.
 * The function also returns the absolute index of the `=` sign if it exists
 */
private fun DocContent.`find $tags`(): List<Pair<IntRange, Int?>> {
    val text = this

    return buildList {
        var escapeNext = false
        for ((i, char) in text.withIndex()) {
            when {
                escapeNext -> escapeNext = false

                char == '\\' -> escapeNext = true

                char == '$' -> {
                    val (key, value) = text.substring(startIndex = i).findKeyAndValueFromDollarSign()

                    this += if (value == null) { // reporting "$key" range
                        Pair(
                            first = i..<i + key.length + 1,
                            second = null,
                        )
                    } else { // reporting "$key=value" range
                        Pair(
                            first = i..<i + key.length + value.length + 2,
                            second = i + 1 + key.length,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Given a "$[string starting with] dollar sign notation", it
 * returns the first key of the notation, in this case
 * ("[string starting with]", null).
 *
 * If the notation is "$[string starting with]=value something", it returns
 * ("[string starting with]", "value").
 *
 * If the notation is "${[string starting with]=value something}", it returns
 * ("[string starting with]", "value something").
 *
 * Semantics:
 *
 * key, value:
 * - `anything ][}{}]` don't open inner []{} scopes
 * - {[][`}`{}} anything but } (unless opened by {), unless in ``, don't open [] scopes
 * - [ [] {]}`}[]][]]`] anything but ] unless opened or in {} or ``
 * - [][] same as above, just twice
 * - only first char can open some of these scopes from no-scope
 * - { cannot be used as first char in key unless in brackets
 *
 * key:
 * - letters, digits, or _, no whitespace
 *
 * '=' is splitter
 *
 * value (inside ${}):
 * - anything but }, can open scopes
 *
 * value (outside $):
 * - letters, digits, or _, no whitespace
 *
 */
fun String.findKeyAndValueFromDollarSign(): KeyAndValue<String, String?> {
    require(startsWith('$')) { "String must start with a dollar sign" }

    val isInBrackets = startsWith("\${") && endsWith("}")
    val content = when (isInBrackets) {
        true -> removePrefix("\${").removeSuffix("}")
        false -> removePrefix("$")
    }

    val blocksIndicators = mutableListOf<Char>()
    fun isInQuoteBlock() = BACKTICKS in blocksIndicators
    fun isInSquareBrackets() = SQUARE_BRACKETS in blocksIndicators
    fun isInCurlyBraces() = CURLY_BRACES in blocksIndicators

    var key = ""
    var value: String? = null
    var timesInSquareBrackets = 0
    var readyForSecondSquareBracket = false
    var escapeNext = false

    fun isWritingValue() = value != null
    fun isWritingKey() = value == null
    fun appendChar(char: Char) {
        if (isWritingValue()) value += char
        else key += char
    }

    fun startWritingValue() {
        require(isWritingKey()) { "Cannot start writing value twice" }
        value = ""
        timesInSquareBrackets = 0
    }

    for ((i, char) in content.withIndex()) {
        // can be the first char of the key or value, this allows notations like $`hello there`=[something else]
        val isFirstChar = i == 0 || value == ""
        when {
            escapeNext -> {
                escapeNext = false
                appendChar(char)
            }

            char == '\\' -> { // next character can be anything and will simply be added
                escapeNext = true
                appendChar(char)
            }

            isFirstChar && char == '`' -> {
                blocksIndicators += BACKTICKS
                appendChar(char)
            }


            isFirstChar && char == '[' -> {
                blocksIndicators += SQUARE_BRACKETS
                timesInSquareBrackets++
                appendChar(char)
            }

            isFirstChar && char == '{' && (isWritingValue() || isInBrackets) -> {
                blocksIndicators += CURLY_BRACES
                appendChar(char)
            }

            isInQuoteBlock() -> {
                if (char == '`') blocksIndicators.removeAllElementsFromLast(BACKTICKS)
                appendChar(char)
            }

            isInCurlyBraces() -> {
                when (char) {
                    '}' -> blocksIndicators.removeAllElementsFromLast(CURLY_BRACES)
                    '{' -> blocksIndicators += CURLY_BRACES
                    '`' -> blocksIndicators += BACKTICKS
                }
                appendChar(char)
            }

            isInSquareBrackets() -> {
                when (char) {
                    ']' -> {
                        blocksIndicators.removeAllElementsFromLast(SQUARE_BRACKETS)
                        if (!isInSquareBrackets() && timesInSquareBrackets == 1) {
                            readyForSecondSquareBracket = true
                            appendChar(char)
                            continue  // skip it being set to false again
                        }
                    }

                    '[' -> blocksIndicators += SQUARE_BRACKETS
                    '{' -> blocksIndicators += CURLY_BRACES
                    '`' -> blocksIndicators += BACKTICKS
                }
                appendChar(char)
            }

            char == '[' && readyForSecondSquareBracket -> {
                timesInSquareBrackets++
                blocksIndicators += SQUARE_BRACKETS
                appendChar(char)
            }

            isWritingKey() -> {
                when {
                    // move to writing value
                    char == '=' -> startWritingValue()
                    // only letters, digits, or _, no whitespace are allowed
                    Regex("[\\p{L}\\p{N}_]") in char.toString() -> appendChar(char)
                    // stop otherwise
                    else -> break
                }
            }

            isWritingValue() && isInBrackets -> {
                when (char) {
                    '[' -> blocksIndicators += SQUARE_BRACKETS
                    '{' -> blocksIndicators += CURLY_BRACES
                    '`' -> blocksIndicators += BACKTICKS
                    '}' -> break // rogue '}' closes the value writing
                }
                appendChar(char)
            }

            isWritingValue() && !isInBrackets -> {
                when {
                    // only letters, digits, or _, no whitespace are allowed
                    Regex("[\\p{L}\\p{N}_]") in char.toString() -> appendChar(char)
                    // stop otherwise
                    else -> break
                }
            }

            else -> {
                error("should not happen")
            }
        }
        readyForSecondSquareBracket = false
    }

    return KeyAndValue(key, value)
}

data class KeyAndValue<K, V>(
    val key: K,
    val value: V,
)