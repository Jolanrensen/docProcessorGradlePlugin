package nl.jolanrensen.docProcessor

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiJavaFile
import nl.jolanrensen.docProcessor.ProgrammingLanguage.JAVA
import nl.jolanrensen.docProcessor.ProgrammingLanguage.KOTLIN
import org.jetbrains.dokka.analysis.DescriptorDocumentableSource
import org.jetbrains.dokka.analysis.PsiDocumentableSource
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.model.WithSources
import org.jetbrains.dokka.utilities.DokkaLogger
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath
import java.io.File

/**
 * Wrapper around [documentable], [Dokka's Documentable][Documentable],
 * and [source], [Dokka's DocumentableSource][DocumentableSource], that adds easy access
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
 * @property [documentable] The Dokka [Documentable]. Should be given.
 *   This represents the class, function, or another entity that potentially has some doc comment.
 * @property [source] The Dokka [DocumentableSource], either a [PsiDocumentableSource] for Java,
 *   or a [DescriptorDocumentableSource] for Kotlin. Usually provided by a [WithSources] in Dokka. Should be given.
 *   This represents the source of the [documentable] pointing to a language-specific AST/PSI.
 * @property [logger] [Dokka logger][DokkaLogger] that's needed for [findClosestDocComment]. Should be given.
 *
 * @property [sourceHasDocumentation] Whether the original [documentable] has a doc comment or not.
 * @property [fullyQualifiedPath] The fully qualified path of the [documentable], its key if you will.
 * @property [fullyQualifiedExtensionPath] If the documentable is an extension function/property:
 *   "(The path of the receiver).(name of the [documentable])".
 * @property [file] The file in which the [documentable] can be found.
 * @property [docTextRange] The text range of the [file] where the original comment can be found.
 *   This is the range from `/**` to `*/`. If there is no comment, the range is empty. `null` if the [doc comment][DocComment]
 *   could not be found (e.g. because the PSI/AST of the file is not found).
 * @property [docIndent] The amount of spaces the comment is indented with. `null` if the [doc comment][DocComment]
 *   could not be found (e.g. because the PSI/AST of the file is not found).
 *
 * @property [docContent] Just the contents of the comment, without the `*`-stuff. Can be modified with [copy] or via
 *   [asMutable].
 * @property [tags] List of tag names present in this documentable. Can be modified with [copy] or via
 *   [asMutable]. Must be updated manually if [docContent] is modified.
 * @property [isModified] Whether the [docContent] was modified. Can be modified with [copy] or via
 *   [asMutable]. Must be updated manually if [docContent] is modified.
 *
 * @see [MutableDocumentableWrapper]
 */
@Suppress("DataClassPrivateConstructor")
open class DocumentableWrapper internal constructor(
    val documentable: Documentable,
    val source: DocumentableSource,
    internal val logger: DokkaLogger,

    val sourceHasDocumentation: Boolean,
    val fullyQualifiedPath: String,
    val fullyQualifiedExtensionPath: String?,
    val file: File,
    val docTextRange: TextRange,
    val docIndent: Int,

    open val docContent: DocContent,
    open val tags: Set<String>,
    open val isModified: Boolean,
) {

    companion object {
        fun createOrNull(
            documentable: Documentable,
            source: DocumentableSource,
            logger: DokkaLogger,
        ): DocumentableWrapper? {
            val docComment = findClosestDocComment(
                element = source.psi,
                logger = logger,
            )

            val path = documentable.dri.fullyQualifiedPath
            val extensionPath = documentable.dri.fullyQualifiedExtensionPath
            val file = File(source.path)

            if (!file.exists()) {
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
                TextRange(sourceTextRange.startOffset, sourceTextRange.startOffset)
            } catch (_: Throwable) {
                return null
            }

            // calculate the indent of the doc comment by looking at how many spaces are on the first line before /**
            val docIndent = (docTextRange.startOffset -
                    fileText
                        .lastIndexOfNot('\n', docTextRange.startOffset)
                    ).coerceAtLeast(0)

            // grab just the contents of the doc without the *-stuff
            val docContent = docTextRange.substring(fileText).getDocContentOrNull()

            val tags = docContent?.findTagNamesInDocContent() ?: emptyList()
            val isModified = false
            val sourceHasDocumentation = docTextRange.toIntRange().size > 1

            return DocumentableWrapper(
                documentable = documentable,
                source = source,
                logger = logger,
                sourceHasDocumentation = sourceHasDocumentation,
                fullyQualifiedPath = path,
                fullyQualifiedExtensionPath = extensionPath,
                file = file,
                docTextRange = docTextRange,
                docIndent = docIndent,
                docContent = docContent ?: "",
                tags = tags.toSet(),
                isModified = isModified,
            )
        }
    }

    val programmingLanguage: ProgrammingLanguage =
        when (source) {
            is PsiDocumentableSource -> JAVA
            is DescriptorDocumentableSource -> KOTLIN
            else -> error("Unknown source type: ${source::class.simpleName}")
        }

    /** Query file for doc text range. */
    fun queryFileForDocTextRange(): String = docTextRange.substring(file.readText())

    /**
     * Returns all possible paths using [targetPath] and the imports in this file.
     */
    private fun getPathsUsingImports(targetPath: String): List<String> = when (programmingLanguage) {
        JAVA -> {
            val file = source.psi
                ?.containingFile as? PsiJavaFile

            val implicitImports = file?.implicitlyImportedPackages?.toList().orEmpty()
            val writtenImports = file
                ?.importList
                ?.allImportStatements
                ?.toList().orEmpty()

            buildList {
                for (import in implicitImports) {
                    this += "$import.$targetPath"
                }

                for (import in writtenImports) {
                    val qualifiedName = import.importReference?.qualifiedName ?: continue
                    val identifier = import.importReference?.referenceName ?: continue

                    if (import.isOnDemand) {
                        this += "$qualifiedName.$targetPath"
                    } else {
                        this += targetPath.replaceFirst(identifier, qualifiedName)
                    }
                }
            }
        }

        KOTLIN -> {
            val writtenImports = source.psi
                ?.containingFile
                .let { it as? KtFile }
                ?.importDirectives
                ?.mapNotNull { it.importPath }
                ?: emptyList()

            val allImports = writtenImports + ImportPath(FqName("kotlin"), true)

            buildList {
                for (import in allImports) {
                    val qualifiedName = import.pathStr
                    val identifier = import.importedName?.identifier

                    if (import.hasStar) {
                        this += qualifiedName.removeSuffix("*") + targetPath
                    } else if (targetPath.startsWith(identifier!!)) {
                        this += targetPath.replaceFirst(identifier, qualifiedName)
                    }
                }
            }
        }
    }

    /**
     * Returns a list of paths that can be meant for the given [targetPath] in the context of this documentable.
     * It takes the current [fullyQualifiedPath] and [imports][getPathsUsingImports] into account.
     */
    fun getAllFullPathsFromHereForTargetPath(targetPath: String): List<String> {
        val subPaths = buildList {
            val current = fullyQualifiedPath.split(".").toMutableList()
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
            this.addAll(
                getPathsUsingImports(targetPath)
            )

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

        if (programmingLanguage == JAVA) { // support KotlinFileKt.Notation from java
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
     * Queries the [documentables] map for a [DocumentableSource]'s [fullyQualifiedPath] or [fullyQualifiedExtensionPath] that exists for
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
            listOfNotNull(it.fullyQualifiedPath, it.fullyQualifiedExtensionPath)
                .filter { path -> pathIsValid(path, it) }
                .minByOrNull { documentables[it]?.size ?: 0 }
        }
        if (docPath != null) return docPath

        // if there is no doc for the query, then we just return the first matching path
        val queries = getAllFullPathsFromHereForTargetPath(query)

        return queries.firstOrNull { it in documentables }
    }

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
            sourceHasDocumentation = sourceHasDocumentation,
            fullyQualifiedPath = fullyQualifiedPath,
            fullyQualifiedExtensionPath = fullyQualifiedExtensionPath,
            file = file,
            docTextRange = docTextRange,
            docIndent = docIndent,
            docContent = docContent,
            tags = tags,
            isModified = isModified,
        )
}
