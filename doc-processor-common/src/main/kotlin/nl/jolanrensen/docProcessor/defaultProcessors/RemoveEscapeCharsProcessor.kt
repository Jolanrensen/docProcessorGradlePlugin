package nl.jolanrensen.docProcessor.defaultProcessors

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nl.jolanrensen.docProcessor.CompletionInfo
import nl.jolanrensen.docProcessor.DocContent
import nl.jolanrensen.docProcessor.DocProcessor
import nl.jolanrensen.docProcessor.DocumentablesByPath
import nl.jolanrensen.docProcessor.HighlightInfo
import nl.jolanrensen.docProcessor.HighlightType
import nl.jolanrensen.docProcessor.asDocContent
import nl.jolanrensen.docProcessor.getIndicesOfEscapeChars
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

    override val completionInfos: List<CompletionInfo>
        get() = listOf(
            CompletionInfo(
                tag = "\\",
                blockText = "\\",
                presentableBlockText = "\\X",
                moveCaretOffsetBlock = 0,
                inlineText = "\\",
                presentableInlineText = "\\X",
                moveCaretOffsetInline = 0,
                tailText = "Escape X so it's invisible to other preprocessors. \"\\\" will be removed from the doc.",
            ),
        )

    override fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath {
        val mutableDocs = documentablesByPath
            .toMutable()
            .withDocsToProcessFilter { it.sourceHasDocumentation }

        runBlocking {
            mutableDocs
                .documentablesToProcess
                .flatMap { (_, docs) ->
                    docs.map {
                        launch {
                            it.modifyDocContentAndUpdate(
                                it.docContent.value
                                    .removeEscapeCharacters(escapeChars)
                                    .asDocContent(),
                            )
                        }
                    }
                }.joinAll()
        }

        return mutableDocs
    }

    override fun getHighlightsFor(docContent: DocContent): List<HighlightInfo> =
        buildList {
            docContent.value
                .getIndicesOfEscapeChars(escapeChars)
                .forEach {
                    this += buildHighlightInfo(
                        range = it..it,
                        type = HighlightType.BRACKET,
                        tag = "\\",
                    )
                }
        }
}
