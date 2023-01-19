package nl.jolanrensen.docProcessor.defaultProcessors

import nl.jolanrensen.docProcessor.DocProcessor
import nl.jolanrensen.docProcessor.DocumentableWithSource

/**
 * @see SampleDocProcessor
 */
const val SAMPLE_DOC_PROCESSOR = "nl.jolanrensen.docProcessor.defaultProcessors.SampleDocProcessor"

/**
 *
 *
 * @constructor Create empty constructor for sample doc processor
 */
class SampleDocProcessor : DocProcessor {
    override fun process(documentablesByPath: Map<String, List<DocumentableWithSource>>): Map<String, List<DocumentableWithSource>> {
        TODO("Not yet implemented")
    }
}