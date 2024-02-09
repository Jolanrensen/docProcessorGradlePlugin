package nl.jolanrensen.docProcessor

import java.io.File
import java.util.UUID

/**
 * Mutable version of [DocumentableWrapper] for [docContent], [tags], and [isModified].
 *
 * @see DocumentableWrapper
 */
open class MutableDocumentableWrapper(
    programmingLanguage: ProgrammingLanguage,
    imports: List<SimpleImportPath>,
    rawSource: String,
    sourceHasDocumentation: Boolean,
    fullyQualifiedPath: String,
    fullyQualifiedExtensionPath: String?,
    fullyQualifiedSuperPaths: List<String>,
    file: File,
    docFileTextRange: IntRange,
    docIndent: Int,
    identifier: UUID,
    annotationFullyQualifiedPaths: List<String>,
    fileTextRange: IntRange,

    override var docContent: DocContent,
    override var tags: Set<String>,
    override var isModified: Boolean,
) : DocumentableWrapper(
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
    identifier = identifier,
    annotationFullyQualifiedPaths = annotationFullyQualifiedPaths,
    fileTextRange = fileTextRange,
) {

    /**
     * Overwrites [docContent] and updates [isModified] and [tags] if
     * it's different from the current [docContent].
     */
    fun modifyDocContentAndUpdate(newDocContent: DocContent) {
        if (newDocContent == docContent) return
        docContent = newDocContent
        isModified = true
        tags = docContent.findTagNamesInDocContent().toSet()
    }
}

/** Cast or convert current [DocumentableWrapper] to [MutableDocumentableWrapper]. */
fun DocumentableWrapper.toMutable(): MutableDocumentableWrapper =
    if (this is MutableDocumentableWrapper) this
    else MutableDocumentableWrapper(
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
        identifier = identifier,
        annotationFullyQualifiedPaths = annotationFullyQualifiedPaths,
        fileTextRange = fileTextRange,
    )