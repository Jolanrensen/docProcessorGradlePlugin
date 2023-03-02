package nl.jolanrensen.docProcessor

import java.io.Serializable

/**
 * Abstract class that can be used to create a doc processor.
 *
 * Make sure to add the fully qualified name of your class to your
 * (`resources/META-INF/services/nl.jolanrensen.docProcessor.DocProcessor`)
 * to make it visible to the service loader in [ProcessDocsAction].
 *
 * @see TagDocProcessor
 */
abstract class DocProcessor : SimpleLogger, Serializable {

    /**
     * Process given [documentablesByPath].
     * You can use [DocumentableWrapper.copy] to create a new [DocumentableWrapper] with modified
     * [doc content][DocumentableWrapper.docContent] or use [MutableDocumentableWrapper].
     * Mark the docs that were modified with [isModified][DocumentableWrapper.isModified] and
     * don't forget to update [tags][DocumentableWrapper.tags] accordingly.
     *
     * @param parameters The parameters that were passed to the [ProcessDocsAction].
     * @param documentablesByPath Documentables by path
     * @return modified docs by path
     */
    abstract fun process(
        parameters: ProcessDocsAction.Parameters,
        documentablesByPath: Map<String, List<DocumentableWrapper>>,
    ): Map<String, List<DocumentableWrapper>>

    // make logEnabled mutable
    override var logEnabled: Boolean = true
}