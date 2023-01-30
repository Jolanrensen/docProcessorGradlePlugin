package nl.jolanrensen.docProcessor

import com.intellij.openapi.util.TextRange
import org.jetbrains.dokka.analysis.DescriptorDocumentableSource
import org.jetbrains.dokka.analysis.PsiDocumentableSource
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath
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
open class DocumentableWithSource internal constructor(
    val documentable: Documentable,
    val source: DocumentableSource,
    private val logger: DokkaConsoleLogger,

    val docComment: DocComment?,
    val path: String,
    val file: File,
    val fileText: String,
    val docTextRange: TextRange?,
    val docIndent: Int?,

    open val docContent: DocContent,
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
                    TextRange(sourceTextRange.startOffset - 1, sourceTextRange.startOffset)
                } catch (_: Throwable) {
                    null
                }
            }

            val docIndent = docTextRange?.let {
                docTextRange.startOffset - fileText.lastIndexOfNot('\n', docTextRange.startOffset)
            }?.coerceAtLeast(0)

            val docContent = docTextRange?.substring(fileText)?.getDocContent()

            val tags = docContent?.findTagsInDocContent() ?: emptyList()
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
                tags = tags.toSet(),
                isModified = isModified,
            )
        }
    }

    val isJava: Boolean = source is PsiDocumentableSource
    val isKotlin: Boolean = source is DescriptorDocumentableSource

    fun queryFile(): String? = docTextRange?.substring(fileText)

    fun getImports(): List<ImportPath> =
        source.psi
            ?.containingFile
            .let { it as? KtFile }
            ?.importDirectives
            ?.mapNotNull { it.importPath }
            ?: emptyList()

    fun getAllFullPathsFromHereForTargetPath(targetPath: String): List<String> {
        val subPaths = buildList {
            val current = path.split(".").toMutableList()
            while (current.isNotEmpty()) {
                add(current.joinToString("."))
                current.removeLast()
            }
        }

        val queries = buildList {
            // get all possible full @target paths with all possible sub paths
            for (subPath in subPaths) {
                this += targetPath.expandPath(currentFullPath = subPath)
            }

            // check imports too
            for (import in getImports()) {
                val identifier = import.importedName?.identifier

                if (import.hasStar) {
                    this += import.pathStr.removeSuffix("*") + targetPath
                } else if (targetPath.startsWith(identifier!!)) {
                    this += targetPath.replaceFirst(identifier, import.pathStr)
                }
            }
        }

        return queries
    }

    fun asMutable(): MutableDocumentableWithSource =
        if (this is MutableDocumentableWithSource) this
        else MutableDocumentableWithSource(
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

    fun copy(
        docContent: DocContent = this.docContent,
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
            docTextRange = docTextRange,
            docIndent = docIndent,
            docContent = docContent,
            tags = tags,
            isModified = isModified,
        )
}

open class MutableDocumentableWithSource internal constructor(
    documentable: Documentable,
    source: DocumentableSource,
    logger: DokkaConsoleLogger,

    docComment: DocComment?,
    path: String,
    file: File,
    fileText: String,
    docTextRange: TextRange?,
    docIndent: Int?,

    override var docContent: DocContent,
    override var tags: Set<String>,
    override var isModified: Boolean,
) : DocumentableWithSource(
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

