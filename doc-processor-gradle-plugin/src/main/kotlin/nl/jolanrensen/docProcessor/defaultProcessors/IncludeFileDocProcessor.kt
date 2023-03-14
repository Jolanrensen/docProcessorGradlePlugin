package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocumentableWrapper
import nl.jolanrensen.docProcessor.ProgrammingLanguage.JAVA
import nl.jolanrensen.docProcessor.ProgrammingLanguage.KOTLIN
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.getTagArguments
import org.apache.commons.lang.StringEscapeUtils
import java.io.File
import java.io.FileNotFoundException

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
        val extraContent = includeFileArguments.getOrElse(1) { "" }

        val currentDir: File? = documentable.file.parentFile
        val targetFile = currentDir?.resolve(filePath)

        if (targetFile == null || !targetFile.exists())
            throw FileNotFoundException("File $filePath (-> ${targetFile?.absolutePath}) does not exist.")

        if (targetFile.isDirectory)
            throw IllegalArgumentException("File $filePath (-> ${targetFile.absolutePath}) is a directory.")

        val content = targetFile.readText()

        return when (documentable.programmingLanguage) {
            JAVA -> StringEscapeUtils.escapeHtml(content)
                .replace("@", "&#64;")
                .replace("*/", "&#42;&#47;")

            KOTLIN -> content
        }.let {
            if (extraContent.isNotEmpty()) {
                buildString {
                    append(it)
                    if (!extraContent.first().isWhitespace())
                        append(" ")
                    append(extraContent)
                }
            } else it
        }
    }

    /**
     * How to process the `{@includeFile (../tags)}` when it's inline.
     *
     * [processContent] can handle inner tags perfectly fine.
     */
    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = processContent(
        line = tagWithContent,
        documentable = documentable,
        path = path,
    )

    /**
     * How to process the `@includeFile tag` when it's a block tag.
     *
     * [tagWithContent] is the content after the `@includeFile tag`, e.g. `"(../../someFile.txt)"`
     * including any new lines below.
     * We will only replace the first line and skip the rest.
     */
    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = processContent(
        line = tagWithContent,
        documentable = documentable,
        path = path,
    )
}