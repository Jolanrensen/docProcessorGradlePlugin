package nl.jolanrensen.docProcessor.syntaxHighlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.startOffset
import nl.jolanrensen.docProcessor.HighlightInfo
import nl.jolanrensen.docProcessor.HighlightType
import nl.jolanrensen.docProcessor.asDocTextOrNull
import nl.jolanrensen.docProcessor.docProcessorIsEnabled
import nl.jolanrensen.docProcessor.getDocContentWithMap
import nl.jolanrensen.docProcessor.getLoadedProcessors
import nl.jolanrensen.docProcessor.map
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import java.awt.Font
import java.awt.event.KeyEvent
import java.awt.event.KeyListener

/**
 * This class is responsible for highlighting KDoc tags in the editor.
 */
class KDocHighlightAnnotator :
    Annotator,
    CaretListener,
    KeyListener {

    private val isEnabled get() = docProcessorIsEnabled
    private val loadedProcessors = getLoadedProcessors()
    private var initialized = false
    private lateinit var editor: Editor

    override fun caretPositionChanged(event: CaretEvent) = updateRelatedSymbolsHighlighting(editor)

    override fun keyTyped(e: KeyEvent?) = updateRelatedSymbolsHighlighting(editor)

    override fun keyPressed(e: KeyEvent?) = Unit

    override fun keyReleased(e: KeyEvent?) = updateRelatedSymbolsHighlighting(editor)

    private val highlighters = mutableListOf<RangeHighlighter>()

    private fun IntRange.extendLastByOne() = first..last + 1

    private operator fun Int.plus(range: IntRange) = range.first + this..range.last + this

    private operator fun IntRange.plus(int: Int) = this.first + int..this.last + int

    /**
     * Updates the highlighting of related symbols such as brackets.
     */
    @Suppress("ktlint:standard:comment-wrapping")
    private fun updateRelatedSymbolsHighlighting(editor: Editor) {
        highlighters.forEach { it.dispose() }

        val caretOffset = editor.caretModel.offset
        val scheme = EditorColorsManager.getInstance().globalScheme
        val markupModel = editor.markupModel as MarkupModelEx

        val psiFile = PsiDocumentManager.getInstance(editor.project ?: return)
            .getPsiFile(editor.document) ?: return

        val kdoc = psiFile.findElementAt(caretOffset)?.findParentOfType<KDoc>(strict = false) ?: return
        val highlightInfos = getHighlightInfosFor(kdoc)

        val kdocStart = kdoc.startOffset

        val toHighlight = highlightInfos.firstNotNullOfOrNull {
            if (it.related.isNotEmpty() && caretOffset in kdocStart + it.range.extendLastByOne()) {
                it.related + it
            } else {
                null
            }
        } ?: return

        for (it in toHighlight) {
            highlighters += markupModel.addRangeHighlighter(
                /* startOffset = */ kdocStart + it.range.first,
                /* endOffset = */ kdocStart + it.range.last + 1,
                /* layer = */ HighlighterLayer.SELECTION + 100,
                // textAttributes =
                textAttributesFor(it.type).apply {
                    effectType = EffectType.BOXED
                    effectColor = scheme.defaultForeground
                },
                /* targetArea = */ HighlighterTargetArea.EXACT_RANGE,
            )
        }
    }

    private fun textAttributesFor(highlightType: HighlightType): TextAttributes {
        val scheme = EditorColorsManager.getInstance().globalScheme

        val metadataAttributes = scheme.getAttributes(DefaultLanguageHighlighterColors.METADATA)
        val kdocLinkAttributes = scheme.getAttributes(KotlinHighlightingColors.KDOC_LINK)
        val commentAttributes = scheme.getAttributes(KotlinHighlightingColors.BLOCK_COMMENT)
        val declarationAttributes = scheme.getAttributes(DefaultLanguageHighlighterColors.CLASS_NAME)

        return when (highlightType) {
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
        }
    }

    @Suppress("ktlint:standard:comment-wrapping")
    private fun HighlightInfo.createAsAnnotator(kdoc: KDoc, holder: AnnotationHolder) =
        holder
            .newAnnotation(HighlightSeverity.INFORMATION, "Tag from: $tagProcessorName")
            .needsUpdateOnTyping()
            .range(
                TextRange(
                    /* startOffset = */ kdoc.startOffset + range.first,
                    /* endOffset = */ kdoc.startOffset + range.last + 1,
                ),
            )
            .enforcedTextAttributes(textAttributesFor(type))
            .create()

    private fun createAnnotators(kDoc: KDoc, holder: AnnotationHolder) {
        getHighlightInfosFor(kDoc).forEach {
            it.createAsAnnotator(kDoc, holder)
        }
        updateRelatedSymbolsHighlighting(editor)
    }

    private fun getHighlightInfosFor(kdoc: KDoc): List<HighlightInfo> =
        buildList {
            val docText = kdoc.text.asDocTextOrNull() ?: return@buildList

            // convert the doc text to doc content to retrieve the highlights from the processors
            val (docContent, mapping) = docText.getDocContentWithMap()

            for (processor in loadedProcessors) {
                val highlightInfo = processor.getHighlightsFor(docContent)
                    .map(mapping::get) // map back to doc text indices

                addAll(highlightInfo)
            }
        }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!isEnabled) return

        if (!initialized) {
            val editor = element.findExistingEditor()
            if (editor != null) {
                this.editor = editor
                editor.caretModel.addCaretListener(this)
                editor.contentComponent.addKeyListener(this)
                initialized = true
            }
        }

        if (element !is KtDeclaration) return
        val kDoc = element.docComment ?: return

        createAnnotators(kDoc, holder)
    }
}

class KDocHolder(val kDoc: KDoc) : KDoc by kDoc {
    fun overlaps(other: KDocHolder) =
        range.intersects(other.range) || range.contains(other.range) || other.range.contains(range)
}
