package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWithSource
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.docRegex
import nl.jolanrensen.docProcessor.expandPath
import nl.jolanrensen.docProcessor.getTagTarget
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
 * For JavaDoc, the code will be presented as a `<pre>` block with escaped characters.
 *
 * For KDoc, the code will be presented as a Markdown code block with syntax highlighting depending on the language you
 * target.
 */
class SampleDocProcessor : TagDocProcessor() {

    private val sampleTag = "sample"
    private val sampleNoComments = "sampleNoComments"
    override fun tagIsSupported(tag: String): Boolean = tag in listOf(sampleTag, sampleNoComments)

    override fun processTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>
    ): String {
        val noComments = tagWithContent.startsWith("@$sampleNoComments")

        // get the full @sample / @sampleNoComments path
        val samplePath = tagWithContent.let {
            if (noComments) it.getTagTarget(sampleNoComments)
            else it.getTagTarget(sampleTag)
        }

        val sampleQuery = samplePath.expandPath(currentFullPath = path)

        // query all documents for the sample path
        val queried = allDocumentables[sampleQuery]?.firstOrNull()

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

            val currentIsJava = documentable.source is PsiDocumentableSource
            val queriedIsJava = queried.source is PsiDocumentableSource

            buildString {
                if (currentIsJava) {
                    appendLine("<pre>")
                    appendLine(
                        StringEscapeUtils.escapeHtml(queriedSource)
                            .replace("@", "&#64;")
                            .replace("*/", "&#42;&#47;")
                    )
                    appendLine("</pre>")
                } else {
                    append("```")
                    appendLine(if (queriedIsJava) "java" else "kotlin")
                    appendLine(queriedSource)
                    appendLine("```")
                }
            }
        } ?: error("SampleDocProcessor ERROR: Sample not found: $sampleQuery. Called from $path")
    }
}
