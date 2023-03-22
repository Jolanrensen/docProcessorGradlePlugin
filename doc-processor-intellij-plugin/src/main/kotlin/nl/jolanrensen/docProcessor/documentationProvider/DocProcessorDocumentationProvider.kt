package nl.jolanrensen.docProcessor.documentationProvider

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.CompositeDocumentationProvider
import com.intellij.lang.documentation.DocumentationProviderEx
import com.intellij.lang.documentation.ExternalDocumentationProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil.processElements
import com.intellij.psi.util.childrenOfType
import nl.jolanrensen.docProcessor.toDoc
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinDocumentationProvider
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.completion.DeclarationLookupObject
import org.jetbrains.kotlin.idea.core.copied
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.gradleTooling.get
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.idea.kdoc.KotlinExternalDocUrlsProvider
import org.jetbrains.kotlin.idea.kdoc.resolveKDocLink
import org.jetbrains.kotlin.idea.util.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.astReplace
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import java.awt.Image
import java.util.function.Consumer
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class DocProcessorDocumentationProvider : AbstractDocumentationProvider(), ExternalDocumentationProvider {

    private val kotlin = KotlinDocumentationProvider()

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        println("getQuickNavigateInfo $element, $originalElement")
        return "Nothing" // kotlin.getQuickNavigateInfo(element, originalElement)
    }

    override fun getUrlFor(element: PsiElement?, originalElement: PsiElement?): List<String>? {
        println(
            "getUrlFor $element, $originalElement, result: ${
                KotlinExternalDocUrlsProvider.getExternalJavaDocUrl(
                    element
                )
            }"
        )
        return KotlinExternalDocUrlsProvider.getExternalJavaDocUrl(element)
    }

    var customDoc: KDoc? = null
    var fileCopy: KtFile? = null

    override fun collectDocComments(file: PsiFile, sink: Consumer<in PsiDocCommentBase>) {
        println("collectDocComments $file")
        if (file !is KtFile) return

        fileCopy = file.copied()
        customDoc = KDocElementFactory(file.project).createKDocFromText(
            """
            Hello World!
            This is a custom doc comment.
            With working rendering!
            @param test This is a test param
            @sample main
            """.trimIndent().toDoc()
        )
        processElements(file) {
            val comment = (it as? KtDeclaration)?.docComment
            if (comment != null) {
                println("accepting comment $comment, ${comment.text}")
                sink.accept(comment)
            }
            true
        }

        processElements(fileCopy) {
            val comment = (it as? KtDeclaration)?.docComment
            if (comment != null) {
                println("accepting copy comment $comment, ${comment.text}")
                comment.replaced(customDoc!!)
            }
            true
        }
    }


    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        println("generateDoc $element, ${element.text}, $originalElement")

        val kdoc = kotlin.generateDoc(element, originalElement)
        println("result: $kdoc")
        return kdoc
    }

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? {
        println("generateHoverDoc $element, $originalElement")
        return generateDoc(element, originalElement)
    }

    @Nls
    override fun generateRenderedDoc(comment: PsiDocCommentBase): String? {
        println("generateRenderedDoc $comment")
        val kdoc = kotlin.generateRenderedDoc(comment) ?: return null
//        val kdoc = "generated rendered doc"
        return kdoc
    }

    override fun findDocComment(file: PsiFile, range: TextRange): PsiDocCommentBase? {
        println("findDocComment $file, $range")
        val comment = PsiTreeUtil.getParentOfType(
            file.findElementAt(range.startOffset),
            PsiDocCommentBase::class.java, false
        )
        return if (comment == null || range != comment.textRange) null else comment
    }

    override fun getDocumentationElementForLookupItem(
        psiManager: PsiManager,
        `object`: Any?,
        element: PsiElement?,
    ): PsiElement? {
        println("getDocumentationElementForLookupItem $`object`, $element")
        if (`object` is DeclarationLookupObject) {
            `object`.psiElement?.let { return it }
            `object`.descriptor?.let { descriptor ->
                return DescriptorToSourceUtilsIde.getAnyDeclaration(psiManager.project, descriptor)
            }
        }
        return null
    }

    override fun getDocumentationElementForLink(
        psiManager: PsiManager,
        link: String,
        context: PsiElement?,
    ): PsiElement? {
        println("getDocumentationElementForLink $link, $context")
        val navElement = context?.navigationElement as? KtElement ?: return null
        val resolutionFacade = navElement.getResolutionFacade()
        val bindingContext = navElement.safeAnalyzeNonSourceRootCode(resolutionFacade, BodyResolveMode.PARTIAL)
        val contextDescriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, navElement] ?: return null
        val descriptors = resolveKDocLink(
            bindingContext, resolutionFacade,
            contextDescriptor, null, link.split('.')
        )
        val target = descriptors.firstOrNull() ?: return null
        return DescriptorToSourceUtilsIde.getAnyDeclaration(psiManager.project, target)
    }

    @Deprecated("Deprecated in Java")
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?
    ): PsiElement? {
        println("getCustomDocumentationElement DEPR $contextElement, ${contextElement?.text}")

        return kotlin.getCustomDocumentationElement(editor, file, contextElement)
            ?: run {
                if (contextElement == null) return null
                var comment: KDoc? = null
                processElements(fileCopy!!) {
                    if (it is KDoc) {
                        comment = it
                        false
                    } else true
                }
                comment!!.parent
            }
    }

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        println("getCustomDocumentationElement $contextElement")
        return if (this is DocumentationProviderEx) (this as DocumentationProviderEx).getCustomDocumentationElement(
            /* editor = */ editor,
            /* file = */ file,
            /* contextElement = */ contextElement,
        ) else null
    }

    override fun getLocalImageForElement(element: PsiElement, imageSpec: String): Image? {
        println("getLocalImageForElement $element, $imageSpec")
        return null
    }

    @Deprecated("Deprecated in Java")
    override fun hasDocumentationFor(element: PsiElement?, originalElement: PsiElement?): Boolean {
        println("hasDocumentationFor $element, $originalElement")
        return CompositeDocumentationProvider.hasUrlsFor(this, element, originalElement)
    }

    override fun canPromptToConfigureDocumentation(element: PsiElement?): Boolean {
        println("canPromptToConfigureDocumentation $element")
        return false
    }

    override fun promptToConfigureDocumentation(element: PsiElement?) {
        println("promptToConfigureDocumentation $element")
        // do nothing
    }
}