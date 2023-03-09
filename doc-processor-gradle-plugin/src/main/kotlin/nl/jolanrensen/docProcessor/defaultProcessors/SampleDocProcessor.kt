package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWrapper
import nl.jolanrensen.docProcessor.ProgrammingLanguage
import nl.jolanrensen.docProcessor.ProgrammingLanguage.JAVA
import nl.jolanrensen.docProcessor.ProgrammingLanguage.KOTLIN
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.decodeCallableTarget
import nl.jolanrensen.docProcessor.docRegex
import nl.jolanrensen.docProcessor.getTagArguments
import nl.jolanrensen.docProcessor.psi
import org.apache.commons.lang.StringEscapeUtils

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
    override fun tagIsSupported(tag: String): Boolean = tag in listOf(sampleTag, sampleNoComments)

    private val sampleStartRegex = Regex(" *// *SampleStart *\n")
    private val sampleEndRegex = Regex(" *// *SampleEnd *\n")

    private fun processContent(
        line: String,
        documentable: DocumentableWrapper,
        allDocumentables: Map<String, List<DocumentableWrapper>>,
        path: String
    ): String {
        val noComments = line.startsWith("@$sampleNoComments")

        // get the full @sample / @sampleNoComments path
        val sampleArguments = line.getTagArguments(if (noComments) sampleNoComments else sampleTag, 2)
        val samplePath = sampleArguments.first().decodeCallableTarget()
        // for stuff written after the @sample tag, save and include it later
        val extraContent = sampleArguments.getOrElse(1) { "" }.trimStart()

        val queries = documentable.getAllFullPathsFromHereForTargetPath(samplePath)

        // query all documents for the sample path
        val queried = queries.firstNotNullOfOrNull { query ->
            allDocumentables[query]?.firstOrNull()
        }

        return queried?.let {
            val indent = queried.docIndent?.let { " ".repeat(it) } ?: ""
            val rawQueriedSource = queried.source.psi
                ?.text
                ?.let {
                    if (noComments) it.replace(docRegex) { "" }
                    else it
                }
                ?: return@let null
            val queriedSource = (indent + rawQueriedSource).trimIndent()

            val hasSampleComments = sampleStartRegex.containsMatchIn(queriedSource) &&
                    sampleEndRegex.containsMatchIn(queriedSource)

            val content = if (hasSampleComments) {
                val start = sampleStartRegex.find(queriedSource)!!.range.last + 1
                val end = sampleEndRegex.find(queriedSource)!!.range.first - 1
                queriedSource.substring(start, end).trimIndent()
            } else {
                queriedSource
            }

            buildString {
                when (documentable.programmingLanguage) {
                    JAVA -> {
                        appendLine("<pre>")
                        appendLine(
                            StringEscapeUtils.escapeHtml(content)
                                .replace("@", "&#64;")
                                .replace("*/", "&#42;&#47;")
                        )
                        appendLine("</pre>")
                    }

                    KOTLIN -> {
                        append("```")
                        appendLine(
                            when (queried.programmingLanguage) {
                                JAVA -> "java"
                                KOTLIN -> "kotlin"
                            }
                        )
                        appendLine(content)
                        appendLine("```")
                    }
                }

                if (extraContent.isNotBlank())
                    append(" $extraContent")
            }
        } ?: error(
            "SampleDocProcessor ERROR: Sample not found: $samplePath. Called from $path. Attempted queries: [\n${
                queries.joinToString("\n")
            }]"
        )
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
        filteredDocumentables: Map<String, List<DocumentableWrapper>>,
        allDocumentables: Map<String, List<DocumentableWrapper>>
    ): String = processContent(
        line = tagWithContent.removePrefix("{").removeSuffix("}"),
        documentable = documentable,
        allDocumentables = allDocumentables,
        path = path,
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
        filteredDocumentables: Map<String, List<DocumentableWrapper>>,
        allDocumentables: Map<String, List<DocumentableWrapper>>
    ): String = tagWithContent.split('\n').mapIndexed { i, line ->
        if (i == 0) {
            processContent(
                line = line.trimStart(),
                documentable = documentable,
                allDocumentables = allDocumentables,
                path = path,
            )
        } else {
            line
        }
    }.joinToString("\n")
}
