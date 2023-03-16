package nl.jolanrensen.docProcessor

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.resolve.ImportPath
import java.io.File

/**
 * Mutable version of [DocumentableWrapper] for [docContent], [tags], and [isModified].
 *
 * @see DocumentableWrapper
 */
open class MutableDocumentableWrapper(
    programmingLanguage: ProgrammingLanguage,
    imports: List<ImportPath>,
    rawSource: String,
    sourceHasDocumentation: Boolean,
    fullyQualifiedPath: String,
    fullyQualifiedExtensionPath: String?,
    file: File,
    docTextRange: TextRange,
    docIndent: Int,

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
    file = file,
    docTextRange = docTextRange,
    docIndent = docIndent,
    docContent = docContent,
    tags = tags,
    isModified = isModified,
)

/** Cast or convert current [DocumentableWrapper] to [MutableDocumentableWrapper]. */
fun DocumentableWrapper.asMutable(): MutableDocumentableWrapper =
    if (this is MutableDocumentableWrapper) this
    else MutableDocumentableWrapper(
        programmingLanguage = programmingLanguage,
        imports = imports,
        rawSource = rawSource,
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