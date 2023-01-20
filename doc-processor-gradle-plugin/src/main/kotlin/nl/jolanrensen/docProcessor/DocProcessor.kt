package nl.jolanrensen.docProcessor

import java.io.Serializable

fun interface DocProcessor : Serializable {

    /**
     * Process given [documentablesByPath].
     * You can use [DocumentableWithSource.copy] to create a new [DocumentableWithSource] with modified
     * [DocumentableWithSource.docContent].
     * Mark the docs that were modified with [DocumentableWithSource.isModified] and
     * don't forget to update [DocumentableWithSource.tags] accordingly.
     *
     * @param parameters The parameters that were passed to the [ProcessDocsAction].
     * @param documentablesByPath Documentables by path
     * @return modified docs by path
     */
    fun process(
        parameters: ProcessDocsAction.Parameters,
        documentablesByPath: Map<String, List<DocumentableWithSource>>,
    ): Map<String, List<DocumentableWithSource>>
}