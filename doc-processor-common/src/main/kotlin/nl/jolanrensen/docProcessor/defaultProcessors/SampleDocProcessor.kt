package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWrapper
import nl.jolanrensen.docProcessor.ProgrammingLanguage
import nl.jolanrensen.docProcessor.ProgrammingLanguage.JAVA
import nl.jolanrensen.docProcessor.ProgrammingLanguage.KOTLIN
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.decodeCallableTarget
import nl.jolanrensen.docProcessor.docRegex
import nl.jolanrensen.docProcessor.getTagArguments
import nl.jolanrensen.docProcessor.withoutFilters
import org.apache.commons.text.StringEscapeUtils

/**
 * @see SampleDocProcessor
 */
const val SAMPLE_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.SampleDocProcessor"

/**
 * Introduces @sample and @sampleNoComments tags.
 *
 * `@sample` will include the code of the target element in the docs entirely.
 *
 * `@sampleNoComments` will include the code of the target element in the docs, but will remove all its comments first.
 *
 * If, in the target, the comments "`// SampleStart`" and "`// SampleEnd`" exist, only the code between those comments
 * will be included.
 *
 * For JavaDoc, the code will be presented as a `<pre>` block with escaped characters.
 *
 * For KDoc, the code will be presented as a Markdown code block with syntax highlighting depending on the language you
 * target.
 *
 * `@sample` and `@sampleNoComments` keeps the content of the block below the statement intact.
 */
class SampleDocProcessor : TagDocProcessor() {

    private val sampleTag = "sample"
    private val sampleNoComments = "sampleNoComments"
    private val supportedTags = listOf(sampleTag, sampleNoComments)

    override fun tagIsSupported(tag: String): Boolean = tag in supportedTags

    private val sampleStartRegex = Regex(" *// *SampleStart *\n")
    private val sampleEndRegex = Regex(" *// *SampleEnd *\n")

    private fun processContent(tagWithContent: String, documentable: DocumentableWrapper): String {
        val unfilteredDocumentablesByPath by lazy { documentablesByPath.withoutFilters() }
        val noComments = tagWithContent.startsWith("{@$sampleNoComments") ||
            tagWithContent.trimStart().startsWith("@$sampleNoComments ")

        // get the full @sample / @sampleNoComments path
        val sampleArguments = tagWithContent.getTagArguments(
            tag = if (noComments) sampleNoComments else sampleTag,
            numberOfArguments = 2,
        )

        val samplePath = sampleArguments.first().decodeCallableTarget()

        // for stuff written after the @sample tag, save and include it later
        val extraContent = sampleArguments.getOrElse(1) { "" }

        val queries = documentable.getAllFullPathsFromHereForTargetPath(
            targetPath = samplePath,
            documentablesNoFilters = unfilteredDocumentablesByPath,
        )

        // query all documents for the sample path
        val targetDocumentable = queries.firstNotNullOfOrNull { query ->
            documentablesByPath.query(query, documentable)?.firstOrNull()
        } ?: throwError(samplePath, queries)

        // get the source text of the target documentable, optionally trimming to between the
        // sampleStart and sampleEnd comments
        val targetSourceText = getTargetSourceText(
            targetDocumentable = targetDocumentable,
            noComments = noComments,
        ).getContentBetweenSampleComments()

        // convert the source text to the correct comment content for the current language
        val commentContent = convertSourceTextToCommentContent(
            sampleSourceText = targetSourceText,
            sampleLanguage = targetDocumentable.programmingLanguage,
            currentLanguage = documentable.programmingLanguage,
        )

        // add extra content back if it existed
        return if (extraContent.isNotEmpty()) {
            buildString {
                append(commentContent)
                if (!extraContent.first().isWhitespace()) {
                    append(" ")
                }
                append(extraContent)
            }
        } else {
            commentContent
        }
    }

    private fun throwError(samplePath: String, queries: List<String>): Nothing =
        error(
            """
            |Reference not found: $samplePath. 
            |Attempted queries: [
            ${queries.joinToString("\n") { "|  $it" }}
            ]
            """.trimMargin(),
        )

    /**
     * Returns the entire source text of the entire [targetDocumentable], optionally
     * removing docs at the start with [noComments].
     * Returns `null` if the source text cannot be found.
     */
    private fun getTargetSourceText(targetDocumentable: DocumentableWrapper, noComments: Boolean): String {
        val indent = " ".repeat(targetDocumentable.docIndent)
        val rawQueriedSource = targetDocumentable.rawSource.let {
            if (noComments) {
                it.replace(docRegex) { "" }
            } else {
                it
            }
        }
        return (indent + rawQueriedSource).trimIndent()
    }

    /**
     * Trims the source text to only the code between the `// SampleStart` and `// SampleEnd` comments.
     * If no such comments exist, the entire source text is returned.
     */
    private fun String.getContentBetweenSampleComments(): String {
        val hasSampleComments = sampleStartRegex.containsMatchIn(this) &&
            sampleEndRegex.containsMatchIn(this)

        return if (hasSampleComments) {
            val start = sampleStartRegex
                .findAll(this)
                .first()
                .range
                .last + 1
            val end = sampleEndRegex
                .findAll(this)
                .last()
                .range
                .first - 1
            this.substring(start, end).trimIndent()
        } else {
            this
        }
    }

    /**
     * Converts the [sample source text][sampleSourceText] to the correct comment content
     * for the [sample source text language][sampleLanguage] and
     * [current language][currentLanguage].
     */
    private fun convertSourceTextToCommentContent(
        sampleSourceText: String,
        sampleLanguage: ProgrammingLanguage,
        currentLanguage: ProgrammingLanguage,
    ): String =
        buildString {
            when (currentLanguage) {
                JAVA -> {
                    appendLine("<pre>")
                    appendLine(
                        StringEscapeUtils
                            .escapeHtml4(sampleSourceText)
                            .replace("@", "&#64;")
                            .replace("*/", "&#42;&#47;"),
                    )
                    append("</pre>")
                }

                KOTLIN -> {
                    append("```")
                    appendLine(
                        when (sampleLanguage) {
                            JAVA -> "java"
                            KOTLIN -> "kotlin"
                        },
                    )
                    appendLine(sampleSourceText)
                    append("```")
                }
            }
        }

    /**
     * How to process the `@sample tag` when it's an inline tag.
     *
     * [processContent] can handle inner tags perfectly fine.
     */
    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String =
        processContent(
            tagWithContent = tagWithContent,
            documentable = documentable,
        )

    /**
     * How to process the `@sample tag` when it's a block tag.
     *
     * [tagWithContent] is the content after the `@sample tag`, e.g. `"[SomeClass]"`
     * including any new lines below.
     * We will only replace the first line and skip the rest.
     */
    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String =
        processContent(
            tagWithContent = tagWithContent,
            documentable = documentable,
        )
}
