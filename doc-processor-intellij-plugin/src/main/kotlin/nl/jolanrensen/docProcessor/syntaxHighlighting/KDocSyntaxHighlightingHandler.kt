//@file:Suppress("UnstableApiUsage")
//
//package nl.jolanrensen.docProcessor.syntaxHighlighting
//
//import com.intellij.lang.documentation.QuickDocSyntaxHighlightingHandler
//import com.intellij.lang.documentation.QuickDocSyntaxHighlightingHandlerFactory
//import com.intellij.openapi.editor.colors.EditorColorsScheme
//import com.intellij.openapi.editor.markup.TextAttributes
//import com.intellij.openapi.project.Project
//import com.intellij.psi.PsiFile
//import com.intellij.psi.util.PsiTreeUtil
//import com.intellij.psi.util.startOffset
//import com.intellij.ui.JBColor
//import nl.jolanrensen.docProcessor.services.DocProcessorServiceK2
//import org.jetbrains.kotlin.kdoc.psi.api.KDoc
//import org.jetbrains.kotlin.psi.KtDeclaration
//import org.jetbrains.kotlin.psi.KtFile
//import java.awt.Font
//
///**
// * This seems to handle Rendered KDoc inside code blocks inside KDocs.
// * TODO
// */
//class KDocSyntaxHighlightingHandler : QuickDocSyntaxHighlightingHandler {
//
//    private val serviceInstances: MutableMap<Project, DocProcessorServiceK2> = mutableMapOf()
//
//    private fun getService(project: Project) =
//        serviceInstances.getOrPut(project) { DocProcessorServiceK2.getInstance(project) }
//
//    override fun performSemanticHighlighting(
//        file: PsiFile,
//    ): List<QuickDocSyntaxHighlightingHandler.QuickDocHighlightInfo> {
//        if (file !is KtFile) return emptyList()
//
//        val service = getService(file.project)
//        if (!service.isEnabled) return emptyList()
//        val result = mutableListOf<QuickDocHighlightInfo>()
//        PsiTreeUtil.processElements(file) {
//            val owner = it as? KtDeclaration ?: return@processElements true
//            val originalDocumentation = owner.docComment as KDoc? ?: return@processElements true
//            result += getHighlightInfoForKDoc(originalDocumentation)
//            true
//        }
//
//        return result
//    }
//
//    fun getHighlightInfoForKDoc(kDoc: KDoc): List<QuickDocHighlightInfo> =
//        buildList {
//            val startOffset = kDoc.startOffset
//
//            for ((i, char) in kDoc.text.withIndex()) {
//                if (char == 'a') {
//                    this += QuickDocHighlightInfo(
//                        startOffset = startOffset + i,
//                        endOffset = startOffset + i + 1,
//                    ) { scheme ->
//                        TextAttributes(
//                            // foregroundColor =
//                            JBColor.RED,
//                            // backgroundColor =
//                            null,
//                            // effectColor =
//                            null,
//                            // effectType =
//                            null,
//                            // fontType =
//                            Font.BOLD,
//                        )
//                    }
//                }
//            }
//        }
//
//    override fun postProcessHtml(html: String): String = super.postProcessHtml(html)
//
//    override fun preprocessCode(code: String): String = super.preprocessCode(code)
//}
//
//data class QuickDocHighlightInfo(
//    override val startOffset: Int,
//    override val endOffset: Int,
//    val textAttributes: (scheme: EditorColorsScheme) -> TextAttributes?,
//) : QuickDocSyntaxHighlightingHandler.QuickDocHighlightInfo {
//    override fun getTextAttributes(scheme: EditorColorsScheme): TextAttributes? = textAttributes(scheme)
//}
//
//class KDocSyntaxHighlightingHandlerFactory : QuickDocSyntaxHighlightingHandlerFactory {
//    override fun createHandler(): QuickDocSyntaxHighlightingHandler = KDocSyntaxHighlightingHandler()
//}
