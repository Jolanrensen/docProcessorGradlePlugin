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
    private fun getService(project: Project) = serviceInstances
        .getOrPut(project) { DocProcessorService.getInstance(project) }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        println("getQuickNavigateInfo $element, $originalElement")
        return kotlin.getQuickNavigateInfo(element, originalElement)
    }

    override fun getUrlFor(element: PsiElement?, originalElement: PsiElement?): List<String>? {
        println("getUrlFor $element, $originalElement")
        return kotlin.getUrlFor(element, originalElement)
    }

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
        println("generateDoc $element, ${element.text}, $originalElement")
        val modifiedElement = getService(element.project).getModifiedElement(element)
        return kotlin.generateDoc(modifiedElement ?: element, originalElement)
    }

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? {
        println("generateHoverDoc $element, $originalElement")
        return super.generateDoc(element, originalElement)
    }

    @Nls
    override fun generateRenderedDoc(comment: PsiDocCommentBase): String? {
        println("generateRenderedDoc $comment")
        return kotlin.generateRenderedDoc(comment)
    }

    override fun findDocComment(file: PsiFile, range: TextRange): PsiDocCommentBase? {
        println("findDocComment $file, $range")
        return kotlin.findDocComment(file, range)
    }

    override fun getDocumentationElementForLookupItem(
        psiManager: PsiManager,
        `object`: Any?,
        element: PsiElement?,
    ): PsiElement? {
        println("getDocumentationElementForLookupItem $`object`, $element")
        return kotlin.getDocumentationElementForLookupItem(psiManager, `object`, element)
    }

    override fun getDocumentationElementForLink(
        psiManager: PsiManager,
        link: String,
        context: PsiElement?,
    ): PsiElement? {
        println("getDocumentationElementForLink $link, $context")
        return kotlin.getDocumentationElementForLink(psiManager, link, context)
    }

    @Deprecated("Deprecated in Java")
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?
    ): PsiElement? {
        println("getCustomDocumentationElement DEPR $contextElement, ${contextElement?.text}")
        return kotlin.getCustomDocumentationElement(editor, file, contextElement)
    }

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        println("getCustomDocumentationElement $contextElement")
        return getCustomDocumentationElement(
            /* editor = */ editor,
            /* file = */ file,
            /* contextElement = */ contextElement,
        )
    }

    override fun getLocalImageForElement(element: PsiElement, imageSpec: String): Image? {
        println("getLocalImageForElement $element, $imageSpec")
        return kotlin.getLocalImageForElement(element, imageSpec)
    }

    @Deprecated("Deprecated in Java")
    override fun hasDocumentationFor(element: PsiElement?, originalElement: PsiElement?): Boolean {
        println("hasDocumentationFor $element, $originalElement")
        return CompositeDocumentationProvider.hasUrlsFor(this, element, originalElement)
    }

    override fun canPromptToConfigureDocumentation(element: PsiElement?): Boolean {
        println("canPromptToConfigureDocumentation $element")
        return kotlin.canPromptToConfigureDocumentation(element)
    }

    override fun promptToConfigureDocumentation(element: PsiElement?) {
        println("promptToConfigureDocumentation $element")
        kotlin.promptToConfigureDocumentation(element)
    }
}