package nl.jolanrensen.docProcessor

import com.intellij.openapi.util.TextRange
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import java.io.File

/**
 * Mutable version of [DocumentableWrapper] for [docContent], [tags], and [isModified].
 *
 * @see DocumentableWrapper
 */
open class MutableDocumentableWrapper internal constructor(
    documentable: Documentable,
    source: DocumentableSource,
    logger: DokkaConsoleLogger,

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

/** Cast or convert current [DocumentableWrapper] to [MutableDocumentableWrapper]. */
fun DocumentableWrapper.asMutable(): MutableDocumentableWrapper =
    if (this is MutableDocumentableWrapper) this
    else MutableDocumentableWrapper(
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