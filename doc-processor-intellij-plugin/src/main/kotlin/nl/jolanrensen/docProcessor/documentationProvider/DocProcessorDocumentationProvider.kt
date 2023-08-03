package nl.jolanrensen.docProcessor.documentationProvider

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.CompositeDocumentationProvider
import com.intellij.lang.documentation.ExternalDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil.processElements
import nl.jolanrensen.docProcessor.services.DocProcessorService
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinDocumentationProvider
import org.jetbrains.kotlin.psi.*
import java.awt.Image
import java.util.function.Consumer

class DocProcessorDocumentationProvider : AbstractDocumentationProvider(), ExternalDocumentationProvider {

    private val kotlin = KotlinDocumentationProvider()

    private val serviceInstances: MutableMap<Project, DocProcessorService> = mutableMapOf()
    private fun getService(project: Project) =
        serviceInstances.getOrPut(project) { DocProcessorService.getInstance(project) }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? =
        kotlin.getQuickNavigateInfo(element, originalElement)

    override fun getUrlFor(element: PsiElement?, originalElement: PsiElement?): List<String>? =
        kotlin.getUrlFor(element, originalElement)

    override fun collectDocComments(file: PsiFile, sink: Consumer<in PsiDocCommentBase>) {
        if (file !is KtFile) return

        // capture all comments in the file
        processElements(file) {
            val comment = (it as? KtDeclaration)?.docComment
            if (comment != null) {
                sink.accept(comment)
            }
            true
        }
    }

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        val modifiedElement = getService(element.project).getModifiedElement(element)
        return try {
            kotlin.generateDoc(modifiedElement ?: element, originalElement)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? =
        super.generateDoc(element, originalElement)

    @Nls
    override fun generateRenderedDoc(comment: PsiDocCommentBase): String? = kotlin.generateRenderedDoc(comment)

    override fun findDocComment(file: PsiFile, range: TextRange): PsiDocCommentBase? =
        kotlin.findDocComment(file, range)

    override fun getDocumentationElementForLookupItem(
        psiManager: PsiManager,
        `object`: Any?,
        element: PsiElement?,
    ): PsiElement? = kotlin.getDocumentationElementForLookupItem(psiManager, `object`, element)

    override fun getDocumentationElementForLink(
        psiManager: PsiManager,
        link: String,
        context: PsiElement?,
    ): PsiElement? = kotlin.getDocumentationElementForLink(psiManager, link, context)

    @Deprecated("Deprecated in Java")
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
    ): PsiElement? = kotlin.getCustomDocumentationElement(editor, file, contextElement)

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? =
        getCustomDocumentationElement(
            editor = editor,
            file = file,
            contextElement = contextElement,
        )

    override fun getLocalImageForElement(element: PsiElement, imageSpec: String): Image? =
        kotlin.getLocalImageForElement(element, imageSpec)

    @Deprecated("Deprecated in Java")
    override fun hasDocumentationFor(element: PsiElement?, originalElement: PsiElement?): Boolean =
        CompositeDocumentationProvider.hasUrlsFor(this, element, originalElement)

    override fun canPromptToConfigureDocumentation(element: PsiElement?): Boolean =
        kotlin.canPromptToConfigureDocumentation(element)

    override fun promptToConfigureDocumentation(element: PsiElement?): Unit =
        kotlin.promptToConfigureDocumentation(element)

}