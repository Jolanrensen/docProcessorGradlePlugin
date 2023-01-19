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
     * @param documentablesByPath Documentables by path
     * @return modified docs by path
     */
    fun process(documentablesByPath: Map<String, List<DocumentableWithSource>>): Map<String, List<DocumentableWithSource>>
}