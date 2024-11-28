package nl.jolanrensen.docProcessor.syntaxHighlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.startOffset
import nl.jolanrensen.docProcessor.HighlightInfo
import nl.jolanrensen.docProcessor.HighlightType
import nl.jolanrensen.docProcessor.docProcessorIsEnabled
import nl.jolanrensen.docProcessor.getLoadedProcessors
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import java.awt.Font
import com.intellij.codeInsight.daemon.impl.HighlightInfo as IjHighlightInfo

/**
 * This class is responsible for highlighting KDoc tags in the editor.
 */
class KDocHighlightVisitor : HighlightVisitor {

    val isEnabled get() = docProcessorIsEnabled

    private var highlightInfoHolder: HighlightInfoHolder? = null

    val loadedProcessors = getLoadedProcessors()

    override fun suitableForFile(file: PsiFile): Boolean = isEnabled && file is KtFile

    @Suppress("ktlint:standard:comment-wrapping")
    fun HighlightInfo.toIntelliJHighlightInfoOrNull(kdoc: KDoc): IjHighlightInfo? {
        val scheme = EditorColorsManager.getInstance().globalScheme

        val metadataAttributes = scheme.getAttributes(DefaultLanguageHighlighterColors.METADATA)
        val kdocLinkAttributes = scheme.getAttributes(KotlinHighlightingColors.KDOC_LINK)
        val commentAttributes = scheme.getAttributes(KotlinHighlightingColors.BLOCK_COMMENT)
        val declarationAttributes = scheme.getAttributes(DefaultLanguageHighlighterColors.CLASS_NAME)

        return IjHighlightInfo.newHighlightInfo(
            HighlightInfoType.HighlightInfoTypeImpl(
                HighlightSeverity.WEAK_WARNING,
                DefaultLanguageHighlighterColors.METADATA,
                true,
            ),
        ).textAttributes(
            when (type) {
                HighlightType.BRACKET ->
                    metadataAttributes.clone().apply {
                        fontType = Font.BOLD + Font.ITALIC
                    }

                HighlightType.TAG ->
                    metadataAttributes.clone().apply {
                        fontType = Font.BOLD + Font.ITALIC
                        effectType = EffectType.LINE_UNDERSCORE
                        effectColor = metadataAttributes.foregroundColor
                    }

                HighlightType.TAG_KEY ->
                    kdocLinkAttributes.clone().apply {}

                HighlightType.TAG_VALUE ->
                    declarationAttributes.clone().apply {}

                HighlightType.COMMENT ->
                    commentAttributes.clone().apply {
                        fontType = Font.BOLD + Font.ITALIC
                    }

                HighlightType.COMMENT_TAG ->
                    commentAttributes.clone().apply {
                        fontType = Font.BOLD + Font.ITALIC
                        effectType = EffectType.LINE_UNDERSCORE
                        effectColor = commentAttributes.foregroundColor
                    }
            },
        )
            .range(kdoc, kdoc.startOffset + range.first, kdoc.startOffset + range.last + 1)
            .create()
    }

    fun getHighlightInfoForKDoc(kDoc: KDoc): List<IjHighlightInfo> =
        buildList<IjHighlightInfo?> {
            for (processor in loadedProcessors) {
                val highlightInfo = processor.getHighlightsFor(kDoc.text)
                for (info in highlightInfo) {
                    val ijHighlightInfo = info.toIntelliJHighlightInfoOrNull(kDoc)
                    if (ijHighlightInfo != null) {
                        this += ijHighlightInfo
                    }
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
            // e.printStackTrace()
        } finally {
            highlightInfoHolder = null
        }
        return true
    }

    override fun clone() = KDocHighlightVisitor()
}
