package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocProcessor
import nl.jolanrensen.docProcessor.DocumentableWrapper
import nl.jolanrensen.docProcessor.ProcessDocsAction

/**
 * @see TodoDocProcessor
 */
const val TODO_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.TodoDocProcessor"

/**
 * A doc processor that adds a doc with `TODO`
 * where the docs are missing.
 */
class TodoDocProcessor : DocProcessor() {
    override fun process(
        processLimit: Int,
        documentablesByPath: Map<String, List<DocumentableWrapper>>
    ): Map<String, List<DocumentableWrapper>> =
        documentablesByPath.mapValues { (_, documentables) ->
            documentables.map {
                if (it.docContent.isBlank() || !it.sourceHasDocumentation) {
                    it.copy(
                        docContent = "TODO",
                        isModified = true,
                    )
                } else it
            }
        }
}

