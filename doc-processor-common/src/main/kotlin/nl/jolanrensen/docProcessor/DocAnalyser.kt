package nl.jolanrensen.docProcessor

/**
 * Same as [DocProcessor] but without the ability to modify docs.
 */
abstract class DocAnalyser<out R> : DocProcessor() {

    abstract fun getAnalyzedResult(): R

    protected abstract fun analyze(processLimit: Int, documentablesByPath: DocumentablesByPath)

    fun analyzeSafely(processLimit: Int, documentablesByPath: DocumentablesByPath): DocAnalyser<R> {
        processSafely(processLimit, documentablesByPath)
        return this
    }

    final override fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath {
        analyze(processLimit, documentablesByPath)
        return documentablesByPath
    }
}
