package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocProcessor
import nl.jolanrensen.docProcessor.DocumentableWithSource

/**
 * @see TodoDocProcessor
 */
const val TODO_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.TodoDocProcessor"

/**
 * A doc processor that adds a doc with `TODO`
 * where the docs are missing.
 * NOTE: Currently it's placed in front.
 */
class TodoDocProcessor : DocProcessor {
    override fun process(
        documentablesByPath: Map<String, List<DocumentableWithSource>>
    ): Map<String, List<DocumentableWithSource>> =
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

