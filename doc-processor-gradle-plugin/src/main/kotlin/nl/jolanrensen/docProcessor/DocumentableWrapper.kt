package nl.jolanrensen.docProcessor

import com.intellij.openapi.util.TextRange
import org.jetbrains.dokka.analysis.DescriptorDocumentableSource
import org.jetbrains.dokka.analysis.PsiDocumentableSource
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath
import java.io.File

/**
 * Wrapper around [Documentable][documentable] that adds easy access
 * to several useful properties and functions.
 *
 * Instantiate it with [documentable], [source] and [logger] using
 * [DocumentableWrapper.createOrNull][Companion.createOrNull].
 *
 * [docContent], [tags], and [isModified] are designed te be changed and will be read when
 * writing modified docs to files.
 * Modify either in immutable fashion using [copy], or in mutable fashion using [asMutable].
 *
 * All other properties are read-only and based upon the source-documentable.
 *
 * @property documentable The documentable. Should be given.
 * @property source The source. Should be given.
 * @property logger Dokka logger that's needed for [findClosestDocComment]. Should be given.
 *
 * @property docComment The doc comment found from [documentable] with [findClosestDocComment]
 * @property path The path of the [documentable].
 * @property extensionPath If the documentable is an extension function/property:
 *   "(The path of the receiver).(name of the [documentable])".
 * @property file The file of the [documentable]'s [source].
 * @property fileText The text of the [documentable]'s [file].
 * @property docTextRange The text range of the [file] where the original comment can be found.
 * @property docIndent The amount of spaces the comment is indented with.
 *
 * @property docContent Just the contents of the comment, without the `*`-stuff.
 * @property tags List of tag names present in this documentable.
 * @property isModified Whether the [docContent] was modified.
 *
 * @see MutableDocumentableWrapper
 */
@Suppress("DataClassPrivateConstructor")
open class DocumentableWrapper internal constructor(
    val documentable: Documentable,
    val source: DocumentableSource,
    private val logger: DokkaConsoleLogger,

    val docComment: DocComment?,
    val path: String,
    val extensionPath: String?,
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
        ): DocumentableWrapper? {
            val docComment = findClosestDocComment(
                element = source.psi,
                logger = logger,
            )

            val path = documentable.dri.path
            val extensionPath = documentable.dri.extensionPath
            val file = File(source.path)

            if (!file.exists()) {
                // println("Warning: Could not find file for $path")
                return null
            }

            val fileText: String = file.readText()

            val docTextRange = docComment?.textRange?.let { ogRange ->
                // docComment.textRange is the range of the comment in the file, but depending on the language,
                // it might not exactly match the range of the comment from /** to */. Let's correct that.
                val query = ogRange.substring(fileText)
                val startComment = query.indexOf("/**")
                val endComment = query.lastIndexOf("*/")

                require(startComment != -1) {
                    """
                    |Could not find start of comment.
                    |Paths: ${listOfNotNull(path, extensionPath)}
                    |Comment Content: "${docComment.documentString}"
                    |Query: "$query"""".trimMargin()
                }
                require(endComment != -1) {
                    """
                    |Could not find end of comment.
                    |Paths: ${listOfNotNull(path, extensionPath)}
                    |Comment Content: "${docComment.documentString}"
                    |Query: "$query"""".trimMargin()
                }

                TextRange(ogRange.startOffset + startComment, ogRange.startOffset + endComment + 2)
            } ?: try {
                // if there is no comment, we give the text range for where a new comment could be.
                // throws an exception if it's not in the file
                val sourceTextRange = source.textRange!!

                TextRange(sourceTextRange.startOffset - 1, sourceTextRange.startOffset)
            } catch (_: Throwable) {
                null
            }

            // calculate the indent of the doc comment by looking at how many spaces are on the first line before /**
            val docIndent = docTextRange?.let {
                docTextRange.startOffset - fileText.lastIndexOfNot('\n', docTextRange.startOffset)
            }?.coerceAtLeast(0)

            // grab just the contents of the doc without the *-stuff
            val docContent = docTextRange?.substring(fileText)?.getDocContentOrNull()

            val tags = docContent?.findTagNamesInDocContent() ?: emptyList()
            val isModified = false

            return DocumentableWrapper(
                documentable = documentable,
                source = source,
                logger = logger,
                docComment = docComment,
                path = path,
                extensionPath = extensionPath,
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

    /** Query file for doc text range. */
    fun queryFileForDocTextRange(): String? = docTextRange?.substring(fileText)

    /** Returns all imports available to this documentable. */
    fun getImports(): List<ImportPath> =
        source.psi
            ?.containingFile
            .let { it as? KtFile }
            ?.importDirectives
            ?.mapNotNull { it.importPath }
            ?: emptyList()

    /**
     * Returns a list of paths that can be meant for the given [targetPath] in the context of this documentable.
     * It takes the current [path] and [imports][getImports] into account.
     */
    fun getAllFullPathsFromHereForTargetPath(targetPath: String): List<String> {
        val subPaths = buildList {
            val current = path.split(".").toMutableList()
            while (current.isNotEmpty()) {
                add(current.joinToString("."))
                current.removeLast()
            }
        }

        val queries = buildList {
            // get all possible full target paths with all possible sub paths
            for (subPath in subPaths) {
                this += "$subPath.$targetPath"
            }

            // check imports too
            val imports = getImports() + ImportPath(FqName("kotlin"), true)
            for (import in imports) {
                val identifier = import.importedName?.identifier

                if (import.hasStar) {
                    this += import.pathStr.removeSuffix("*") + targetPath
                } else if (targetPath.startsWith(identifier!!)) {
                    this += targetPath.replaceFirst(identifier, import.pathStr)
                }
            }

            // finally, add the path itself in case it's a top level/fq path
            this += targetPath
        }

        return queries
    }

    /**
     * Queries the [documentables] map for a [DocumentableWrapper] that exists for
     * the given [query].
     * Returns `null` if no [DocumentableWrapper] is found for the given [query].
     */
    fun queryDocumentables(
        query: String,
        documentables: Map<String, List<DocumentableWrapper>>,
        filter: (DocumentableWrapper) -> Boolean = { true },
    ): DocumentableWrapper? {
        val queries = getAllFullPathsFromHereForTargetPath(query).toMutableList()

        if (isJava) { // support KotlinFileKt.Notation from java
            val splitQuery = query.split(".")
            if (splitQuery.firstOrNull()?.endsWith("Kt") == true) {
                queries += getAllFullPathsFromHereForTargetPath(
                    splitQuery.drop(1).joinToString(".")
                )
            }
        }

        return queries.firstNotNullOfOrNull {
            documentables[it]?.firstOrNull(filter)
        }
    }

    /**
     * Queries the [documentables] map for a [DocumentableSource]'s [path] or [extensionPath] that exists for
     * the given [query]. If there is no [DocumentableSource] for the given [query] but the path
     * still exists as a key in the [documentables] map, then that path is returned.
     */
    fun queryDocumentablesForPath(
        query: String,
        documentables: Map<String, List<DocumentableWrapper>>,
        pathIsValid: (String, DocumentableWrapper) -> Boolean = { _, _ -> true },
        filter: (DocumentableWrapper) -> Boolean = { true },
    ): String? {
        val docPath = queryDocumentables(query, documentables, filter)?.let {
            // take either the normal path to the doc or the extension path depending on which is valid and
            // causes the smallest number of collisions
            listOfNotNull(it.path, it.extensionPath)
                .filter { path -> pathIsValid(path, it) }
                .minByOrNull { documentables[it]?.size ?: 0 }
        }
        if (docPath != null) return docPath

        // if there is no doc for the query, then we just return the first matching path
        val queries = getAllFullPathsFromHereForTargetPath(query)

        return queries.firstOrNull { it in documentables }
    }

    /** Cast or convert current [DocumentableWrapper] to [MutableDocumentableWrapper]. */
    fun asMutable(): MutableDocumentableWrapper =
        if (this is MutableDocumentableWrapper) this
        else MutableDocumentableWrapper(
            documentable = documentable,
            source = source,
            logger = logger,
            docComment = docComment,
            path = path,
            extensionPath = extensionPath,
            file = file,
            fileText = fileText,
            docTextRange = docTextRange,
            docIndent = docIndent,
            docContent = docContent,
            tags = tags,
            isModified = isModified,
        )

    /** Returns a copy of this [DocumentableWrapper] with the given parameters. */
    fun copy(
        docContent: DocContent = this.docContent,
        tags: Set<String> = this.tags,
        isModified: Boolean = this.isModified,
    ): DocumentableWrapper =
        DocumentableWrapper(
            documentable = documentable,
            source = source,
            logger = logger,
            docComment = docComment,
            path = path,
            extensionPath = extensionPath,
            file = file,
            fileText = fileText,
            docTextRange = docTextRange,
            docIndent = docIndent,
            docContent = docContent,
            tags = tags,
            isModified = isModified,
        )
}

/**
 * Mutable version of [DocumentableWrapper] for [docContent], [tags], and [isModified].
 *
 * @see DocumentableWrapper
 */
open class MutableDocumentableWrapper internal constructor(
    documentable: Documentable,
    source: DocumentableSource,
    logger: DokkaConsoleLogger,

    docComment: DocComment?,
    path: String,
    extensionPath: String?,
    file: File,
    fileText: String,
    docTextRange: TextRange?,
    docIndent: Int?,

    override var docContent: DocContent,
    override var tags: Set<String>,
    override var isModified: Boolean,
) : DocumentableWrapper(
    documentable = documentable,
    source = source,
    logger = logger,
    docComment = docComment,
    path = path,
    extensionPath = extensionPath,
    file = file,
    fileText = fileText,
    docTextRange = docTextRange,
    docIndent = docIndent,
    docContent = docContent,
    tags = tags,
    isModified = isModified,
)
