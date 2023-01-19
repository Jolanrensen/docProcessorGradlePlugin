package nl.jolanrensen.docProcessor

import java.io.Serializable

fun interface DocProcessor : Serializable {

    /**
     * Process given [docsByPath].
     * You can use [DocumentableWithSource.copy] to create a new [DocumentableWithSource] with modified
     * [DocumentableWithSource.docContent].
     * Mark the docs that were modified with [DocumentableWithSource.isModified] and
     * don't forget to update [DocumentableWithSource.tags] accordingly.
     *
     * @param docsByPath Docs by path
     * @return modified docs by path
     */
    fun process(docsByPath: Map<String, List<DocumentableWithSource>>): Map<String, List<DocumentableWithSource>>
}