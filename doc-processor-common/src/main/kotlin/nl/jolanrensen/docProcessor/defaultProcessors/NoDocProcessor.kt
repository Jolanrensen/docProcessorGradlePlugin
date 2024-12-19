package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocProcessor
import nl.jolanrensen.docProcessor.DocumentablesByPath
import nl.jolanrensen.docProcessor.asDocContent
import nl.jolanrensen.docProcessor.toDocumentablesByPath

/**
 * @see NoDocProcessor
 */
const val NO_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.NoDocProcessor"

/**
 * A doc processor that simply removes all docs from the sources.
 */
class NoDocProcessor : DocProcessor() {
    override fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath =
        documentablesByPath
            .documentablesToProcess
            .map { (path, documentables) ->
                path to documentables.map {
                    it.copy(
                        tags = emptySet(),
                        docContent = "".asDocContent(),
                        isModified = true,
                    )
                }
            }.toDocumentablesByPath()
}
