package nl.jolanrensen.docProcessor

import nl.jolanrensen.docProcessor.DocumentableWrapper.Companion
import nl.jolanrensen.docProcessor.ProgrammingLanguage.JAVA
import java.io.File
import java.util.*

/**
 * Wrapper around a [Dokka's Documentable][org.jetbrains.dokka.model.Documentable], that adds easy access
 * to several useful properties and functions.
 *
 * Instantiate it with [documentable][org.jetbrains.dokka.model.Documentable], [source][org.jetbrains.dokka.model.DocumentableSource] and [logger][org.jetbrains.dokka.utilities.DokkaLogger] using
 * [DocumentableWrapper.createFromDokkaOrNull][Companion.createFromDokkaOrNull].
 *
 * [docContent], [tags], and [isModified] are designed te be changed and will be read when
 * writing modified docs to files.
 * Modify either in immutable fashion using [copy], or in mutable fashion using [asMutable].
 *
 * All other properties are read-only and based upon the source-documentable.
 *
 * @property [programmingLanguage] The [programming language][ProgrammingLanguage] of the documentable.
 * @property [imports] The imports of the file in which the documentable can be found.
 * @property [rawSource] The raw source code of the documentable, including documentation. May need to be trimmed.
 * @property [sourceHasDocumentation] Whether the original documentable has a doc comment or not.
 * @property [fullyQualifiedPath] The fully qualified path of the documentable, its key if you will.
 * @property [fullyQualifiedExtensionPath] If the documentable is an extension function/property:
 *   "(The path of the receiver).(name of the documentable)".
 * @property [file] The file in which the documentable can be found.
 * @property [docFileTextRange] The text range of the [file] where the original comment can be found.
 *   This is the range from `/**` to `*/`. If there is no comment, the range is empty. `null` if the [doc comment][DocComment]
 *   could not be found (e.g. because the PSI/AST of the file is not found).
 * @property [docIndent] The amount of spaces the comment is indented with. `null` if the [doc comment][DocComment]
 *   could not be found (e.g. because the PSI/AST of the file is not found).
 * @property [identifier] A unique identifier for this documentable, will survive [copy] and [asMutable].
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
open class DocumentableWrapper(
    val programmingLanguage: ProgrammingLanguage,
    val imports: List<SimpleImportPath>,
    val rawSource: String,
    val sourceHasDocumentation: Boolean,
    val fullyQualifiedPath: String,
    val fullyQualifiedExtensionPath: String?,
    val file: File,
    val docFileTextRange: IntRange,
    val docIndent: Int,
    val identifier: UUID = UUID.randomUUID(),

    open val docContent: DocContent,
    open val tags: Set<String>,
    open val isModified: Boolean,
) {

    companion object;

    constructor(
        docContent: DocContent,
        programmingLanguage: ProgrammingLanguage,
        imports: List<SimpleImportPath>,
        rawSource: String,
        fullyQualifiedPath: String,
        fullyQualifiedExtensionPath: String?,
        file: File,
        docFileTextRange: IntRange,
        docIndent: Int,
    ) : this(
        programmingLanguage = programmingLanguage,
        imports = imports,
        rawSource = rawSource,
        sourceHasDocumentation = docContent.isNotEmpty() && docFileTextRange.size > 1,
        fullyQualifiedPath = fullyQualifiedPath,
        fullyQualifiedExtensionPath = fullyQualifiedExtensionPath,
        file = file,
        docFileTextRange = docFileTextRange,
        docIndent = docIndent,
        docContent = docContent,
        tags = docContent.findTagNamesInDocContent().toSet(),
        isModified = false,
    )

    /** Query file for doc text range. */
    fun queryFileForDocTextRange(): String = file.readText().substring(docFileTextRange)

    /**
     * Returns all possible paths using [targetPath] and the imports in this file.
     */
    private fun getPathsUsingImports(targetPath: String): List<String> = buildList {
        for (import in imports) {
            val qualifiedName = import.pathStr
            val identifier = import.importedName

            if (import.isAllUnder) {
                this += qualifiedName.removeSuffix("*") + targetPath
            } else if (targetPath.startsWith(identifier!!)) {
                this += targetPath.replaceFirst(identifier, qualifiedName)
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
     * Queries the [documentables] map for a [org.jetbrains.dokka.model.DocumentableSource]'s [fullyQualifiedPath] or [fullyQualifiedExtensionPath] that exists for
     * the given [query]. If there is no [org.jetbrains.dokka.model.DocumentableSource] for the given [query] but the path
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
    open fun copy(
        docContent: DocContent = this.docContent,
        tags: Set<String> = this.tags,
        isModified: Boolean = this.isModified,
    ): DocumentableWrapper =
        DocumentableWrapper(
            programmingLanguage = programmingLanguage,
            imports = imports,
            rawSource = rawSource,
            sourceHasDocumentation = sourceHasDocumentation,
            fullyQualifiedPath = fullyQualifiedPath,
            fullyQualifiedExtensionPath = fullyQualifiedExtensionPath,
            file = file,
            docFileTextRange = docFileTextRange,
            docIndent = docIndent,
            docContent = docContent,
            tags = tags,
            isModified = isModified,
            identifier = identifier,
        )
}
