package nl.jolanrensen.docProcessor.syntaxHighlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.startOffset
import nl.jolanrensen.docProcessor.defaultProcessors.`find $tags`
import nl.jolanrensen.docProcessor.defaultProcessors.`find ${}'s`
import nl.jolanrensen.docProcessor.defaultProcessors.findKeyAndValueFromDollarSign
import nl.jolanrensen.docProcessor.docProcessorIsEnabled
import nl.jolanrensen.docProcessor.findBlockTagsInDocContentWithRanges
import nl.jolanrensen.docProcessor.findInlineTagNamesInDocContentWithRanges
import nl.jolanrensen.docProcessor.getTagsInUse
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile

/**
 * This class is responsible for highlighting KDoc tags in the editor.
 */
class KDocHighlightVisitor : HighlightVisitor {

    val bracketElement = HighlightInfoType.HighlightInfoTypeImpl(
        HighlightSeverity.INFORMATION,
        DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP,
    )

    val tagElement = HighlightInfoType.HighlightInfoTypeImpl(
        HighlightSeverity.INFORMATION,
        DefaultLanguageHighlighterColors.DOC_COMMENT_TAG,
    )

    val isEnabled get() = docProcessorIsEnabled

    private var highlightInfoHolder: HighlightInfoHolder? = null
    val tagsInUse = getTagsInUse()

    override fun suitableForFile(file: PsiFile): Boolean = isEnabled && file is KtFile

    fun getHighlightInfoForKDoc(kDoc: KDoc): List<HighlightInfo> =
        buildList<HighlightInfo?> {
            val startOffset = kDoc.startOffset

            // {@inline tags}
            val inlineTags = kDoc.text.findInlineTagNamesInDocContentWithRanges()
            for ((tagName, range) in inlineTags) {
                if (tagName !in tagsInUse) continue

                // Left '{'
                this += HighlightInfo.newHighlightInfo(bracketElement)
                    .range(kDoc, startOffset + range.first, startOffset + range.first + 1)
                    .create()

                // '@' and tag name
                this += HighlightInfo.newHighlightInfo(tagElement)
                    .range(kDoc, startOffset + range.first + 1, startOffset + range.first + 2 + tagName.length)
                    .create()

                // Right '}'
                this += HighlightInfo.newHighlightInfo(bracketElement)
                    .range(kDoc, startOffset + range.last, startOffset + range.last + 1)
                    .create()
            }

            // @block tags
            val blockTags = kDoc.text.findBlockTagsInDocContentWithRanges()
            for ((tagName, range) in blockTags) {
                if (tagName !in tagsInUse) continue

                // '@' and tag name
                this += HighlightInfo.newHighlightInfo(tagElement)
                    .range(kDoc, startOffset + range.first, startOffset + range.first + 1 + tagName.length)
                    .create()
            }

            // ${tags}
            val bracedDollarTags = kDoc.text.`find ${}'s`()
            for (range in bracedDollarTags) {
                // '$'
                this += HighlightInfo.newHighlightInfo(tagElement)
                    .range(kDoc, startOffset + range.first, startOffset + range.first + 1)
                    .create()

                // '{'
                this += HighlightInfo.newHighlightInfo(bracketElement)
                    .range(kDoc, startOffset + range.first + 1, startOffset + range.first + 2)
                    .create()

                // `=`
                val (key, value) = kDoc.text.substring(range).findKeyAndValueFromDollarSign()
                if (value != null) { // null if there is no '='
                    val equalsPosition = range.first + 2 + key.length
                    this += HighlightInfo.newHighlightInfo(tagElement)
                        .range(kDoc, startOffset + equalsPosition, startOffset + equalsPosition + 1)
                        .create()
                }

                // '}'
                this += HighlightInfo.newHighlightInfo(bracketElement)
                    .range(kDoc, startOffset + range.last, startOffset + range.last + 1)
                    .create()
            }

            // $tags=...
            val dollarTags = kDoc.text.`find $tags`()
            for ((range, equalsLocation) in dollarTags) {
                // '$'
                this += HighlightInfo.newHighlightInfo(tagElement)
                    .range(kDoc, startOffset + range.first, startOffset + range.first + 1)
                    .create()

                // '='
                if (equalsLocation != null) {
                    this += HighlightInfo.newHighlightInfo(tagElement)
                        .range(kDoc, startOffset + equalsLocation, startOffset + equalsLocation + 1)
                        .create()
                }
            }
        }.filterNotNull()

    override fun visit(element: PsiElement) {
        if (!isEnabled) return
        if (highlightInfoHolder == null) return
        if (element !is KtDeclaration) return
        val kDoc = element.docComment ?: return

        val highlightInfo = getHighlightInfoForKDoc(kDoc)
        for (info in highlightInfo) {
            highlightInfoHolder!!.add(info)
        }
    }

    override fun analyze(
        file: PsiFile,
        updateWholeFile: Boolean,
        holder: HighlightInfoHolder,
        action: Runnable,
    ): Boolean {
        if (!isEnabled) return true
        highlightInfoHolder = holder
        try {
            action.run()
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            highlightInfoHolder = null
        }
        return true
    }

    override fun clone() = KDocHighlightVisitor()
}
