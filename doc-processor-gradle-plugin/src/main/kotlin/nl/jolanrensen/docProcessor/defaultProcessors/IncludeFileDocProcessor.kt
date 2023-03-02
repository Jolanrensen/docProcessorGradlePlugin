package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWrapper
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.getTagArguments
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.dokka.analysis.PsiDocumentableSource

/**
 * @see IncludeFileDocProcessor
 */
const val INCLUDE_FILE_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.IncludeFileDocProcessor"

/**
 * This tag doc processor will include the contents of a file in the docs.
 * For example:
 * `@includeFile (../../someFile.txt)`
 *
 * `@includeFile` keeps the content of the block below the includeFile statement intact.
 *
 * TODO include settings for filtering in the file, optional triple quotes, etc.
 */
class IncludeFileDocProcessor : TagDocProcessor() {

    private val tag = "includeFile"
    override fun tagIsSupported(tag: String): Boolean =
        tag == this.tag

    private fun processContent(
        line: String,
        documentable: DocumentableWrapper,
        path: String,
    ): String {
        val includeFileArguments = line.getTagArguments(
            tag = tag,
            numberOfArguments = 2,
        )
        val filePath = includeFileArguments.first()
            .trim()

            // TODO figure out how to make file location clickable
            // TODO both for Java and Kotlin
            .removePrefix("(")
            .removeSuffix(")")
            .trim()

        // for stuff written after the @includeFile tag, save and include it later
        val extraContent = includeFileArguments.getOrElse(1) { "" }.trimStart()

        val currentDir = documentable.file.parentFile!!
        val targetFile = currentDir.resolve(filePath)

        if (!targetFile.exists()) error("IncludeFileProcessor ERROR: File $filePath (-> ${targetFile.absolutePath}) does not exist. Called from $path.")
        if (targetFile.isDirectory) error("IncludeFileProcessor ERROR: File $filePath (-> ${targetFile.absolutePath}) is a directory. Called from $path.")

        val content = targetFile.readText()
        val currentIsJava = documentable.source is PsiDocumentableSource

        return if (currentIsJava) {
            StringEscapeUtils.escapeHtml(content)
                .replace("@", "&#64;")
                .replace("*/", "&#42;&#47;")
        } else {
            content
        }.let {
            if (extraContent.isNotBlank()) "$it $extraContent"
            else it
        }
    }

    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
        filteredDocumentables: Map<String, List<DocumentableWrapper>>,
        allDocumentables: Map<String, List<DocumentableWrapper>>
    ): String = processContent(
        line = tagWithContent,
        documentable = documentable,
        path = path,
    )

    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
        filteredDocumentables: Map<String, List<DocumentableWrapper>>,
        allDocumentables: Map<String, List<DocumentableWrapper>>
    ): String = tagWithContent
        .split('\n')
        .mapIndexed { i, line ->
            if (i == 0) {
                processContent(
                    line = line,
                    documentable = documentable,
                    path = path,
                )
            } else {
                line
            }
        }
        .joinToString("\n")
}