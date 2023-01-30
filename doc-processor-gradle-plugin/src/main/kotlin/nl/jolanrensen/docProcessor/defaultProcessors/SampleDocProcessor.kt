package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWithSource
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.docRegex
import nl.jolanrensen.docProcessor.expandPath
import nl.jolanrensen.docProcessor.getTagTarget
import nl.jolanrensen.docProcessor.hasStar
import nl.jolanrensen.docProcessor.psi
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.dokka.analysis.PsiDocumentableSource

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
        tagWithContent: String,
        line: String,
        documentable: DocumentableWithSource,
        allDocumentables: Map<String, List<DocumentableWithSource>>,
        path: String
    ): String {
        val noComments = tagWithContent.startsWith("@$sampleNoComments")

        // get the full @sample / @sampleNoComments path
        val samplePath = line.getTagTarget(if (noComments) sampleNoComments else sampleTag)

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
                queriedSource.substring(start, end)
            } else {
                queriedSource
            }

            buildString {
                if (documentable.isJava) {
                    appendLine("<pre>")
                    appendLine(
                        StringEscapeUtils.escapeHtml(content)
                            .replace("@", "&#64;")
                            .replace("*/", "&#42;&#47;")
                    )
                    appendLine("</pre>")
                } else {
                    append("```")
                    appendLine(if (queried.isJava) "java" else "kotlin")
                    appendLine(content)
                    appendLine("```")
                }
            }
        } ?: error(
            "SampleDocProcessor ERROR: Sample not found: $samplePath. Called from $path. Attempted queries: [\n${
                queries.joinToString("\n")
            }]"
        )
    }

    override fun processInnerTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String = processContent(
        tagWithContent = tagWithContent.removePrefix("{").removeSuffix("}"),
        line = docContent,
        documentable = documentable,
        allDocumentables = allDocumentables,
        path = path,
    )

    override fun processTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String = tagWithContent.split('\n').mapIndexed { i, line ->
        // tagWithContent is the content after the @sample tag, e.g. "[SomeClass]"
        // including any new lines below. We will only replace the first line and skip the rest.
        if (i == 0) {
            processContent(
                tagWithContent = tagWithContent.trimStart(),
                line = line,
                documentable = documentable,
                allDocumentables = allDocumentables,
                path = path,
            )
        } else {
            line
        }
    }.joinToString("\n")
}
