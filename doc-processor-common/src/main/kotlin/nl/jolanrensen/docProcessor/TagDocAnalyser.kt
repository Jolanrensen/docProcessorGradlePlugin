package nl.jolanrensen.docProcessor

/**
 * Version of TagDocProcessor which does not modify any tags,
 * but instead just allows you to visit all tags.
 */
abstract class TagDocAnalyser<out R> : TagDocProcessor() {

    abstract fun analyseBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    )

    abstract fun analyseInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    )

    // Needs to call process!
    abstract fun analyze(processLimit: Int, documentablesByPath: DocumentablesByPath): R

    final override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String {
        analyseBlockTagWithContent(tagWithContent, path, documentable)
        return tagWithContent
    }

    final override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String {
        analyseInlineTagWithContent(tagWithContent, path, documentable)
        return tagWithContent
    }

    final override fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath =
        super.process(processLimit, documentablesByPath)

    // we only need one iteration for analyzing as no modifications can be made
    final override fun shouldContinue(i: Int, anyModifications: Boolean, processLimit: Int): Boolean {
        val processLimitReached = i >= processLimit

        // Throw error if process limit is reached or if supported tags keep being present but no modifications are made
        if (processLimitReached)
            onProcessError()

        return false
    }
}