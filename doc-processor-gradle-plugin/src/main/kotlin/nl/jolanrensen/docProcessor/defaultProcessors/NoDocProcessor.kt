package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocProcessor
import nl.jolanrensen.docProcessor.DocumentableWithSource


/**
 * @see NoDocProcessor
 */
const val NO_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.NoDocProcessor"


/**
 * A doc processor that simply removes all docs from the sources.
 */
class NoDocProcessor : DocProcessor {
    override fun process(
        documentablesByPath: Map<String, List<DocumentableWithSource>>,
    ): Map<String, List<DocumentableWithSource>> =
        documentablesByPath.mapValues { (_, documentables) ->
            documentables.map {
                it.copy(
                    tags = emptyList(),
                    docContent = "",
                    isModified = true,
                )
            }
        }
}
