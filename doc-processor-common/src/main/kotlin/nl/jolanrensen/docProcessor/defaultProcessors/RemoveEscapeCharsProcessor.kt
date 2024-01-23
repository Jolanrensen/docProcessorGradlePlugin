package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocProcessor
import nl.jolanrensen.docProcessor.DocumentablesByPath
import nl.jolanrensen.docProcessor.removeEscapeCharacters

/**
 * @see RemoveEscapeCharsProcessor
 */
const val REMOVE_ESCAPE_CHARS_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.RemoveEscapeCharsProcessor"

/**
 * Removes escape characters ('\') from all the docs.
 *
 * Escape characters can also be "escaped" by being repeated.
 * For example, `\\` will be replaced by `\`.
 */
class RemoveEscapeCharsProcessor : DocProcessor() {

    private val escapeChars = listOf('\\')
    override fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath {
        val mutableDocs = documentablesByPath
            .toMutable()
            .withDocsToProcessFilter { it.sourceHasDocumentation }

        mutableDocs.documentablesToProcess.forEach { (_, docs) ->
            docs.forEach {
                it.docContent = it.docContent.removeEscapeCharacters(escapeChars)
            }
        }
        return mutableDocs
    }
}