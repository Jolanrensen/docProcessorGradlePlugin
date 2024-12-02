package nl.jolanrensen.docProcessor

import nl.jolanrensen.docProcessor.DocumentableWrapper.Companion
import nl.jolanrensen.docProcessor.ProgrammingLanguage.JAVA
import java.io.File
import java.util.UUID

/**
 * Wrapper around a [Dokka's Documentable][org.jetbrains.dokka.model.Documentable], that adds easy access
 * to several useful properties and functions.
 *
 * Instantiate it with [documentable][org.jetbrains.dokka.model.Documentable], [source][org.jetbrains.dokka.model.DocumentableSource] and [logger][org.jetbrains.dokka.utilities.DokkaLogger] using
 * [DocumentableWrapper.createFromDokkaOrNull][Companion.createFromDokkaOrNull].
 *
 * [docContent], [tags], and [isModified] are designed te be changed and will be read when
 * writing modified docs to files.
 * Modify either in immutable fashion using [copy], or in mutable fashion using [toMutable].
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
 * @property [fullyQualifiedSuperPaths] The fully qualified paths of the super classes of the documentable.
 * @property [file] The file in which the documentable can be found.
 * @property [docFileTextRange] The text range of the [file] where the original comment can be found.
 *   This is the range from `/**` to `*/`. If there is no comment, the range is empty. `null` if the [doc comment][DocComment]
 *   could not be found (e.g. because the PSI/AST of the file is not found).
 * @property [docIndent] The amount of spaces the comment is indented with. `null` if the [doc comment][DocComment]
 *   could not be found (e.g. because the PSI/AST of the file is not found).
 * @property [identifier] A unique identifier for this documentable, will survive [copy] and [asMutable].
 * @property [annotations] A list of annotations present on this documentable.
 * @property [fileTextRange] The range in the file this documentable is defined in.
 *
 * @property [docContent] Just the contents of the comment, without the `*`-stuff. Can be modified with [copy] or via
 *   [toMutable].
 * @property [tags] List of tag names present in this documentable. Can be modified with [copy] or via
 *   [toMutable]. Must be updated manually if [docContent] is modified.
 * @property [isModified] Whether the [docContent] was modified. Can be modified with [copy] or via
 *   [toMutable]. Must be updated manually if [docContent] is modified.
 *
 * @property [htmlRangeStart] Optional begin marker used by [ExportAsHtmlDocProcessor] for the
 *   [@ExportAsHtml][ExportAsHtml] annotation.
 * @property [htmlRangeEnd] Optional end marker used by [ExportAsHtmlDocProcessor] for the
 *   [@ExportAsHtml][ExportAsHtml] annotation.
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
    val fullyQualifiedSuperPaths: List<String>,
    val file: File,
    val docFileTextRange: IntRange,
    val docIndent: Int,
    val annotations: List<AnnotationWrapper>,
    val fileTextRange: IntRange,
    val identifier: UUID = computeIdentifier(
        imports = imports,
        file = file,
        fullyQualifiedPath = fullyQualifiedPath,
        fullyQualifiedExtensionPath = fullyQualifiedExtensionPath,
        fullyQualifiedSuperPaths = fullyQualifiedSuperPaths,
        textRangeStart = fileTextRange.first,
    ),
    val origin: Any,
    open val docContent: DocContent,
    open val tags: Set<String>,
    open val isModified: Boolean,
    open val htmlRangeStart: Int?,
    open val htmlRangeEnd: Int?,
) {

    companion object {

        /**
         * Computes a unique identifier for a documentable based on its [fullyQualifiedPath] and
         * its [fullyQualifiedExtensionPath].
         */
        fun computeIdentifier(
            imports: List<SimpleImportPath>,
            file: File,
            fullyQualifiedPath: String,
            fullyQualifiedExtensionPath: String?,
            fullyQualifiedSuperPaths: List<String>,
            textRangeStart: Int,
        ): UUID =
            UUID.nameUUIDFromBytes(
                byteArrayOf(
                    file.path.hashCode().toByte(),
                    fullyQualifiedPath.hashCode().toByte(),
                    fullyQualifiedExtensionPath.hashCode().toByte(),
                    textRangeStart.hashCode().toByte(),
                    *imports.map { it.hashCode().toByte() }.toByteArray(),
                    *fullyQualifiedSuperPaths.map { it.hashCode().toByte() }.toByteArray(),
                ),
            )
    }

    val paths = listOfNotNull(fullyQualifiedPath, fullyQualifiedExtensionPath)

    constructor(
        docContent: DocContent,
        programmingLanguage: ProgrammingLanguage,
        imports: List<SimpleImportPath>,
        rawSource: String,
        fullyQualifiedPath: String,
        fullyQualifiedExtensionPath: String?,
        fullyQualifiedSuperPaths: List<String>,
        file: File,
        docFileTextRange: IntRange,
        docIndent: Int,
        annotations: List<AnnotationWrapper>,
        fileTextRange: IntRange,
        origin: Any,
        htmlRangeStart: Int? = null,
        htmlRangeEnd: Int? = null,
    ) : this(
        programmingLanguage = programmingLanguage,
        imports = imports,
        rawSource = rawSource,
        sourceHasDocumentation = docContent.value.isNotEmpty() && docFileTextRange.size > 1,
        fullyQualifiedPath = fullyQualifiedPath,
        fullyQualifiedExtensionPath = fullyQualifiedExtensionPath,
        fullyQualifiedSuperPaths = fullyQualifiedSuperPaths,
        file = file,
        docFileTextRange = docFileTextRange,
        fileTextRange = fileTextRange,
        docIndent = docIndent,
        docContent = docContent,
        annotations = annotations,
        tags = docContent.findTagNames().toSet(),
        isModified = false,
        htmlRangeStart = htmlRangeStart,
        htmlRangeEnd = htmlRangeEnd,
        origin = origin,
    )

    /** Query file for doc text range. */
    fun queryFileForDocTextRange(): String = file.readText().substring(docFileTextRange)

    /**
     * Returns all possible paths using [targetPath] and the imports in this file.
     */
    private fun getPathsUsingImports(targetPath: String): List<String> =
        buildList {
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

    private var allTypes: Set<DocumentableWrapper>? = null

    /**
     * Retrieves all types of this [DocumentableWrapper], including its supertypes.
     * It caches the results in [allTypes].
     */
    fun getAllTypes(documentables: DocumentablesByPath): Set<DocumentableWrapper> {
        if (allTypes == null) {
            val documentablesNoFilters = documentables.withoutFilters()

            allTypes = buildSet {
                this += this@DocumentableWrapper

                for (path in fullyQualifiedSuperPaths) {
                    documentablesNoFilters.query(path, this@DocumentableWrapper)?.forEach {
                        this += it.getAllTypes(documentablesNoFilters)
                    }
                }
            }
        }
        return allTypes!!
    }

    /**
     * Returns a list of paths that match the given [targetPath] in the context of this documentable.
     * It takes the current [fullyQualifiedPath] and [imports][getPathsUsingImports] into account.
     *
     * For example, given `bar` inside the documentable `Foo`, it would return
     * - `bar`
     * - `Foo.bar`
     * - `FooSuperType.bar`
     * - `package.full.path.bar`
     * - `package.full.bar`
     * - `package.bar`
     * - `someImport.bar`
     * - `someImport2.bar`
     * etc.
     */
    fun getAllFullPathsFromHereForTargetPath(
        targetPath: String,
        documentablesNoFilters: DocumentablesByPath,
        canBeExtension: Boolean = true,
    ): List<String> {
        require(documentablesNoFilters.run { queryFilter == NO_FILTER && documentablesToProcessFilter == NO_FILTER }) {
            "DocumentablesByPath must not have any filters in `getAllFullPathsFromHereForTargetPath()`."
        }
        val paths = getAllTypes(documentablesNoFilters).flatMap { it.paths }
        val subPaths = buildSet {
            for (path in paths) {
                val current = path.split(".").toMutableList()
                while (current.isNotEmpty()) {
                    add(current.joinToString("."))
                    current.removeLast()
                }
            }
        }

        val queries = buildSet {
            // get all possible full target paths with all possible sub paths
            for (subPath in subPaths) {
                this += "$subPath.$targetPath"
            }

            // check imports too
            this.addAll(
                getPathsUsingImports(targetPath),
            )

            // finally, add the path itself in case it's a top level/fq path
            this += targetPath

            // target path could be pointing at something defined on a supertype of the target
            if (!canBeExtension) return@buildSet
            val (targetPathReceiver, target) = targetPath.split(".").let {
                if (it.size <= 1) return@buildSet
                it.dropLast(1).joinToString(".") to it.last()
            }

            // if that is the case, we need to find the type of the receiver and get all full paths from there too
            @Suppress("NamedArgsPositionMismatch")
            val targetType = queryDocumentables(
                query = targetPathReceiver,
                documentablesNoFilters = documentablesNoFilters,
                documentables = documentablesNoFilters,
                canBeExtension = false,
            ) { it != this@DocumentableWrapper } ?: return@buildSet

            val targetTypes = targetType.getAllTypes(documentablesNoFilters)
            addAll(targetTypes.map { "${it.fullyQualifiedPath}.$target" })
        }

        return queries.toList()
    }

    /**
     * Queries the [documentables] map for a [DocumentableWrapper] that exists for
     * the given [query].
     * Returns `null` if no [DocumentableWrapper] is found for the given [query].
     *
     * @param canBeCache Whether the query can be a cache or not. Mosty only used by the
     *   IntelliJ plugin and [IncludeDocProcessor].
     */
    fun queryDocumentables(
        query: String,
        documentablesNoFilters: DocumentablesByPath,
        documentables: DocumentablesByPath,
        canBeExtension: Boolean = true,
        canBeCache: Boolean = false,
        filter: (DocumentableWrapper) -> Boolean = { true },
    ): DocumentableWrapper? {
        val queries: List<String> = buildList {
            if (documentables.needToQueryAllPaths) {
                this += getAllFullPathsFromHereForTargetPath(
                    targetPath = query,
                    documentablesNoFilters = documentablesNoFilters,
                    canBeExtension = canBeExtension,
                )

                if (programmingLanguage == JAVA) { // support KotlinFileKt.Notation from java
                    val splitQuery = query.split(".")
                    if (splitQuery.firstOrNull()?.endsWith("Kt") == true) {
                        this += getAllFullPathsFromHereForTargetPath(
                            targetPath = splitQuery.drop(1).joinToString("."),
                            documentablesNoFilters = documentablesNoFilters,
                            canBeExtension = canBeExtension,
                        )
                    }
                }
            } else {
                this += query
            }
        }

        return queries.firstNotNullOfOrNull {
            documentables.query(
                path = it,
                queryContext = this,
                canBeCache = canBeCache,
            )?.firstOrNull(filter)
        }
    }

    /**
     * Queries the [documentables] map for a [org.jetbrains.dokka.model.DocumentableSource]'s [fullyQualifiedPath] or [fullyQualifiedExtensionPath] that exists for
     * the given [query]. If there is no [org.jetbrains.dokka.model.DocumentableSource] for the given [query] but the path
     * still exists as a key in the [documentables] map, then that path is returned.
     */
    fun queryDocumentablesForPath(
        query: String,
        documentablesNoFilters: DocumentablesByPath,
        documentables: DocumentablesByPath,
        canBeExtension: Boolean = true,
        pathIsValid: (String, DocumentableWrapper) -> Boolean = { _, _ -> true },
        filter: (DocumentableWrapper) -> Boolean = { true },
    ): String? {
        val queryResult = queryDocumentables(
            query = query,
            documentablesNoFilters = documentablesNoFilters,
            documentables = documentables,
            canBeExtension = canBeExtension,
            filter = filter,
        )
        val docPath = queryResult?.let {
            // take either the normal path to the doc or the extension path depending on which is valid and
            // causes the smallest number of collisions
            queryResult.paths
                .filter { path -> pathIsValid(path, queryResult) }
                .minByOrNull { documentables.query(it, this)?.size ?: 0 }
        }

        if (docPath != null) return docPath

        // if there is no doc for the query, then we just return the first matching path
        // this can happen for function overloads with the same name.

        if (queryResult != null) {
            return queryResult.fullyQualifiedPath
        }

        val queries = getAllFullPathsFromHereForTargetPath(
            targetPath = query,
            documentablesNoFilters = documentablesNoFilters,
            canBeExtension = canBeExtension,
        )
        // todo fix for intellij?
        return queries.firstOrNull {
            documentables.query(it, this) != null
        }
    }

    fun getDocContentForHtmlRange(): DocContent {
        val lines = docContent.value.lines()
        val start = htmlRangeStart ?: 0
        val end = htmlRangeEnd ?: lines.lastIndex
        return lines.subList(start, end + 1).joinToString("\n").asDocContent()
    }

    fun getDocHashcode(): Int = docContent.hashCode()

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
            fullyQualifiedSuperPaths = fullyQualifiedSuperPaths,
            file = file,
            docFileTextRange = docFileTextRange,
            docIndent = docIndent,
            docContent = docContent,
            tags = tags,
            isModified = isModified,
            annotations = annotations,
            identifier = identifier,
            fileTextRange = fileTextRange,
            origin = origin,
            htmlRangeStart = htmlRangeStart,
            htmlRangeEnd = htmlRangeEnd,
        )
}
