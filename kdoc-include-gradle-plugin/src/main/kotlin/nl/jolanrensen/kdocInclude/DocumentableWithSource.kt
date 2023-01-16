package nl.jolanrensen.kdocInclude

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiNamedElement
import org.jetbrains.dokka.analysis.DescriptorDocumentableSource
import org.jetbrains.dokka.analysis.PsiDocumentableSource
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
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
 * @property hasInclude Whether the comment has an `@include` tag.
 * @property wasModified Whether the [kdocContent] was modified.
 * @constructor Create [DocumentableWithSource]
 */
internal data class DocumentableWithSource(
    val documentable: Documentable,
    val source: DocumentableSource,
    private val logger: DokkaConsoleLogger,

    val docComment: DocComment? = findClosestDocComment(
        element = source.let { s ->
            when (s) {
                is PsiDocumentableSource -> s.psi
                is DescriptorDocumentableSource -> s.descriptor.findPsi() as PsiNamedElement
                else -> null
            }
        },
        logger = logger,
    ),
    val path: String = documentable.dri.path,
    val file: File = File(source.path),
    val fileText: String = file.readText(),
    val textRange: TextRange = run {
        val ogRange = when (docComment) {
            is JavaDocComment -> docComment.comment.textRange // includes just comment
            is KotlinDocComment -> docComment.descriptor.findPsi()!!.textRange // includes comment and descriptor
            else -> error("Unknown doc comment type")
        }
        val query = ogRange.substring(fileText)
        val startComment = query.indexOf("/**")
        val endComment = query.indexOf("*/")

        require(startComment != -1) { "Could not find start of comment" }
        require(endComment != -1) { "Could not find end of comment" }

        TextRange(ogRange.startOffset + startComment, ogRange.startOffset + endComment + 2)
    },

    val indent: Int = textRange.startOffset - fileText.lastIndexOfNot('\n', textRange.startOffset),
    val kdocContent: String? = docComment?.getDocumentString(),
    val hasInclude: Boolean = docComment?.hasTag(JavadocTag.INCLUDE) ?: false,
    val wasModified: Boolean = false,
) {
    fun queryFile(): String = textRange.substring(file.readText())
}