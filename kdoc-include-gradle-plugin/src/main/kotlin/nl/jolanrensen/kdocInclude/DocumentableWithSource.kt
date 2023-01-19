package nl.jolanrensen.kdocInclude

import com.intellij.openapi.util.TextRange
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.model.doc.Param
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
 * @property textRange The text range of the [file] where the original comment can be found.
 * @property indent The amount of spaces the comment is indented with.
 * @property kdocContent Just the contents of the comment, without the `*`-stuff.
 * @property tags Set of tag names present in this documentable.
 * @property isModified Whether the [kdocContent] was modified.
 * @constructor Create [DocumentableWithSource]
 */
@Suppress("DataClassPrivateConstructor")
open class DocumentableWithSource private constructor(
    open val documentable: Documentable,
    open val source: DocumentableSource,
    private val logger: DokkaConsoleLogger,

    open val docComment: DocComment,
    open val path: String,
    open val file: File,
    open val fileText: String,
    open val textRange: TextRange,
    open val indent: Int,

    open val kdocContent: String,
    open val tags: Set<String>,
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

            if (docComment == null) {
                when {
                    documentable.documentation.values.all { it.children.all { it is Param } } -> {
                        // this is a function with only params, so it's probably a constructor
                        // and it doesn't have a doc comment, so we can ignore it
                        println("Warning: Could not find doc comment for ${documentable.dri.path}. This is probably because it's mentioned in an @param and not directly here.")
                    }

                    else -> {
                        println("Warning: Could not find doc comment for ${documentable.dri.path}. It might be defined in a super method.")
                    }
                }

                return null
            }

            val path = documentable.dri.path
            val file = File(source.path)

            if (!file.exists()) {
                println("Warning: Could not find file for $path")
                return null
            }

            val fileText: String = file.readText()

            val ogRange = docComment.textRange

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

            val textRange = TextRange(ogRange.startOffset + startComment, ogRange.startOffset + endComment + 2)
            val indent = textRange.startOffset - fileText.lastIndexOfNot('\n', textRange.startOffset)
            val kdocContent = textRange.substring(fileText).getKdocContent()

            val tags = docComment.tagNames
            val isModified = false

            return DocumentableWithSource(
                documentable = documentable,
                source = source,
                logger = logger,
                docComment = docComment,
                path = path,
                file = file,
                fileText = fileText,
                textRange = textRange,
                indent = indent,
                kdocContent = kdocContent,
                tags = tags.toSet(),
                isModified = isModified,
            )
        }
    }

    fun queryFile(): String = textRange.substring(fileText)

    fun copy(
        kdocContent: String = this.kdocContent,
        tags: Set<String> = this.tags,
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
            textRange = textRange,
            indent = indent,
            kdocContent = kdocContent,
            tags = tags,
            isModified = isModified,
        )
}