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
     * Process given [documentablesByPath]. This function will only be called once per [DocProcessor] instance.
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

    // ensuring each doc processor instance is only run once
    private var hasRun = false

    // ensuring each doc processor instance is only run once
    internal fun processSafely(
        parameters: ProcessDocsAction.Parameters,
        documentablesByPath: Map<String, List<DocumentableWrapper>>,
    ): Map<String, List<DocumentableWrapper>> {
        if (hasRun) error("This instance of ${this::class.qualifiedName} has already run and cannot be reused.")
        hasRun = true
        return process(parameters, documentablesByPath)
    }
}