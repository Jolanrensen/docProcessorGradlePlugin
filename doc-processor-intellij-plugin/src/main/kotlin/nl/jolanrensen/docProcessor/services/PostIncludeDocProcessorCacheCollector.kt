package nl.jolanrensen.docProcessor.services

import com.intellij.psi.PsiElement
import nl.jolanrensen.docProcessor.DocAnalyser
import nl.jolanrensen.docProcessor.DocumentablesByPath
import nl.jolanrensen.docProcessor.DocumentablesByPathWithCache

class PostIncludeDocProcessorCacheCollector(
    private val cacheHolder: DocumentablesByPathWithCache<PsiElement>,
) : DocAnalyser<Unit>() {

    override fun getAnalyzedResult() = Unit

    override fun analyze(processLimit: Int, documentablesByPath: DocumentablesByPath) {
        documentablesByPath.documentablesToProcess.values.forEach {
            it.forEach { documentable ->
                cacheHolder.updatePostIncludeDocContentResult(documentable)
            }
        }
    }
}