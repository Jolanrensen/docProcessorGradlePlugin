package nl.jolanrensen.docProcessor

import com.intellij.openapi.util.TextRange
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import java.io.File

/**
 * Documentable with source. This is a data class to allow .copy() to be used.
 *
 * @property documentable The documentable. Should be given.
 * @property source The source. Should be given.
 * @property logger Dokka logger that's needed for [findClosestDocComment]. Should be given.
 *
 * @property docComment The doc comment found from [documentable] with [findClosestDocComment]
 * @property path The path of the [documentable].
 * @property file The file of the [documentable]'s [source].
 * @property fileText The text of the [documentable]'s [file].
 * @property docTextRange The text range of the [file] where the original comment can be found.
 * @property docIndent The amount of spaces the comment is indented with.
 * @property docContent Just the contents of the comment, without the `*`-stuff.
 * @property tags List of tag names present in this documentable.
 * @property isModified Whether the [docContent] was modified.
 * @constructor Create [DocumentableWithSource]
 */
@Suppress("DataClassPrivateConstructor")
open class DocumentableWithSource private constructor(
    open val documentable: Documentable,
    open val source: DocumentableSource,
    private val logger: DokkaConsoleLogger,

    open val docComment: DocComment?,
    open val path: String,
    open val file: File,
    open val fileText: String,
    open val docTextRange: TextRange?,
    open val docIndent: Int?,

    open val docContent: String,
    open val tags: List<String>,
    open val isModified: Boolean,
) {

    companion object {
        fun createOrNull(
            documentable: Documentable,
            source: DocumentableSource,
            logger: DokkaConsoleLogger,
        ): DocumentableWithSource? {
            val docComment = findClosestDocComment(
                element = source.psi,
                logger = logger,
            )

//            if (docComment == null) {
//                when {
//                    documentable.documentation.values.all { it.children.all { it is Param } } -> {
//                        // this is a function with only params, so it's probably a constructor
//                        // and it doesn't have a doc comment, so we can ignore it
//                        println("Warning: Could not find doc comment for ${documentable.dri.path}. This is probably because it's mentioned in an @param and not directly here.")
//                    }
//
//                    else -> {
//                        println("Warning: Could not find doc comment for ${documentable.dri.path}. It might be defined in a super method.")
//                    }
//                }
//
//                return null
//            }

            val path = documentable.dri.path
            val file = File(source.path)

            if (!file.exists()) {
                println("Warning: Could not find file for $path")
                return null
            }

            val fileText: String = file.readText()

            val ogRange = docComment?.textRange

            val docTextRange = if (ogRange != null) {
                val query = ogRange.substring(fileText)
                val startComment = query.indexOf("/**")
                val endComment = query.lastIndexOf("*/")

                require(startComment != -1) {
                    """
                    |Could not find start of comment.
                    |Path: $path
                    |Comment Content: "${docComment.documentString}"
                    |Query: "$query"""".trimMargin()
                }
                require(endComment != -1) {
                    """
                    |Could not find end of comment.
                    |Path: $path
                    |Comment Content: "${docComment.documentString}"
                    |Query: "$query"""".trimMargin()
                }

                TextRange(ogRange.startOffset + startComment, ogRange.startOffset + endComment + 2)
            } else {
                try {
                    // if there is no comment, we give the text range for where the comment could be.
                    // throws an exception if it's not in the file
                    val sourceTextRange = source.textRange!!
                    TextRange(sourceTextRange.startOffset, sourceTextRange.startOffset)
                } catch (_: Throwable) {
                    null
                }
            }

            val docIndent = docTextRange?.let{ docTextRange.startOffset - fileText.lastIndexOfNot('\n', docTextRange.startOffset)}
            val docContent = docTextRange?.substring(fileText)?.getDocContent()

            val tags = docComment?.tagNames ?: emptyList()
            val isModified = false

            return DocumentableWithSource(
                documentable = documentable,
                source = source,
                logger = logger,
                docComment = docComment,
                path = path,
                file = file,
                fileText = fileText,
                docTextRange = docTextRange,
                docIndent = docIndent,
                docContent = docContent ?: "",
                tags = tags,
                isModified = isModified,
            )
        }
    }

    fun queryFile(): String? = docTextRange?.substring(fileText)

    fun copy(
        docContent: String = this.docContent,
        tags: List<String> = this.tags,
        isModified: Boolean = this.isModified,
    ): DocumentableWithSource =
        DocumentableWithSource(
            documentable = documentable,
            source = source,
            logger = logger,
            docComment = docComment,
            path = path,
            file = file,
            fileText = fileText,
            docTextRange = docTextRange,
            docIndent = docIndent,
            docContent = docContent,
            tags = tags,
            isModified = isModified,
        )
}

