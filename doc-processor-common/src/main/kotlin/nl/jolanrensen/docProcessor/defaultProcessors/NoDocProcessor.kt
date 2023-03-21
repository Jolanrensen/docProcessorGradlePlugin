package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocProcessor
import nl.jolanrensen.docProcessor.DocumentableWrapper


/**
 * @see NoDocProcessor
 */
const val NO_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.NoDocProcessor"


/**
 * A doc processor that simply removes all docs from the sources.
 */
class NoDocProcessor : DocProcessor() {
    override fun process(
        processLimit: Int,
        documentablesByPath: Map<String, List<DocumentableWrapper>>,
    ): Map<String, List<DocumentableWrapper>> =
        documentablesByPath
            .mapValues { (_, documentables) ->
            documentables.map {
                it.copy(
                    tags = emptySet(),
                    docContent = "",
                    isModified = true,
                )
            }
        }
}
