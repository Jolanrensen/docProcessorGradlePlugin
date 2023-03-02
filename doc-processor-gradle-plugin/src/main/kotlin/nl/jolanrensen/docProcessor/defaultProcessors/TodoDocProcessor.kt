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
 * NOTE: Currently it's placed in front.
 */
class TodoDocProcessor : DocProcessor() {
    override fun process(
        parameters: ProcessDocsAction.Parameters,
        documentablesByPath: Map<String, List<DocumentableWrapper>>
    ): Map<String, List<DocumentableWrapper>> =
        documentablesByPath.mapValues { (_, documentables) ->
            documentables.map {
                if (it.docContent.isBlank() && it.docTextRange != null && it.docIndent != null) {
                    it.copy(
                        docContent = "TODO",
                        isModified = true,
                    )
                } else it
            }
        }
}

