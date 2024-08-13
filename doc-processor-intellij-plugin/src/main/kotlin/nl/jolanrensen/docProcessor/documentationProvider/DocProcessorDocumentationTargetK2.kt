package nl.jolanrensen.docProcessor.documentationProvider

import com.intellij.codeInsight.javadoc.JavaDocExternalFilter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.platform.backend.documentation.InlineDocumentationProvider
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import nl.jolanrensen.docProcessor.docComment
import nl.jolanrensen.docProcessor.services.DocProcessorServiceK2
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinDocumentationTargetProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinInlineDocumentationProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinPsiDocumentationTargetProvider
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.renderKDoc
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierList

/*
 * K2
 *
 * check out [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinDocumentationLinkHandler] and
 * [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.resolveKDocLink]
 */

// by element
class DocProcessorPsiDocumentationTargetProvider : PsiDocumentationTargetProvider {

    /**
     * Creates [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinDocumentationTarget]
     */
    val kotlin = KotlinPsiDocumentationTargetProvider()

    private val serviceInstances: MutableMap<Project, DocProcessorServiceK2> = mutableMapOf()

    private fun getService(project: Project) =
        serviceInstances.getOrPut(project) { DocProcessorServiceK2.getInstance(project) }

    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
        val service = getService(element.project)
        if (!service.isEnabled) return kotlin.documentationTarget(element, originalElement)

        val modifiedElement = service.getModifiedElement(element)
        return try {
            kotlin.documentationTarget(modifiedElement ?: element, originalElement)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// by offset in a file
class DocProcessorDocumentationTargetProvider : DocumentationTargetProvider {

    fun PsiElement?.isModifier() =
        this != null &&
            parent is KtModifierList &&
            KtTokens.MODIFIER_KEYWORDS_ARRAY.firstOrNull { it.value == text } != null

    /**
     * Creates [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinDocumentationTarget]
     */
    val kotlin = KotlinDocumentationTargetProvider()
    val kotlinPsi = KotlinPsiDocumentationTargetProvider()

    private val serviceInstances: MutableMap<Project, DocProcessorServiceK2> = mutableMapOf()

    private fun getService(project: Project) =
        serviceInstances.getOrPut(project) { DocProcessorServiceK2.getInstance(project) }

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val service = getService(file.project)
        if (!service.isEnabled) return kotlin.documentationTargets(file, offset)

        val element = file.findElementAt(offset) ?: return emptyList()
        if (!element.isModifier()) return emptyList()

        val modifiedElement = service.getModifiedElement(element)
        if (modifiedElement == null) return emptyList()

        return listOfNotNull(
            try {
                kotlinPsi.documentationTarget(modifiedElement, modifiedElement)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            },
        )
    }
}

class DocProcessorInlineDocumentationProvider : InlineDocumentationProvider {

    class DocProcessorInlineDocumentation(
        private val comment: PsiDocCommentBase,
        private val declaration: KtDeclaration,
    ) : InlineDocumentation {

        val kotlinPsi = KotlinPsiDocumentationTargetProvider()

        override fun getDocumentationRange(): TextRange = comment.textRange

        override fun getDocumentationOwnerRange(): TextRange? = declaration.textRange

        override fun renderText(): String? {
            val docComment = comment as? KDoc ?: return null
            val result = StringBuilder().also {
                it.renderKDoc(docComment.getDefaultSection(), docComment.getAllSections())
            }

            @Suppress("HardCodedStringLiteral")
            return JavaDocExternalFilter.filterInternalDocInfo(result.toString())
        }

        override fun getOwnerTarget(): DocumentationTarget? = kotlinPsi.documentationTarget(declaration, declaration)
    }

    private val serviceInstances: MutableMap<Project, DocProcessorServiceK2> = mutableMapOf()

    private fun getService(project: Project) =
        serviceInstances.getOrPut(project) { DocProcessorServiceK2.getInstance(project) }

    /**
     * Creates [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinInlineDocumentation]
     */
    val kotlin = KotlinInlineDocumentationProvider()

    override fun inlineDocumentationItems(file: PsiFile?): Collection<InlineDocumentation> {
        if (file !is KtFile) return emptyList()

        val service = getService(file.project)
        if (!service.isEnabled) return kotlin.inlineDocumentationItems(file)

        val result = mutableListOf<InlineDocumentation>()
        PsiTreeUtil.processElements(file) {
            val declaration = it as? KtDeclaration
            val modified = declaration?.let { service.getModifiedElement(it) }
            val comment = modified?.docComment as KDoc?
            if (comment != null) {
                result.add(
                    DocProcessorInlineDocumentation(comment, modified as KtDeclaration),
                )
            }
            true
        }

        return result
    }

    override fun findInlineDocumentation(file: PsiFile, textRange: TextRange): InlineDocumentation? {
        val service = getService(file.project)
        if (!service.isEnabled) return kotlin.findInlineDocumentation(file, textRange)

        val comment = PsiTreeUtil.getParentOfType(
            file.findElementAt(textRange.startOffset),
            PsiDocCommentBase::class.java,
        ) ?: return null

        if (comment.textRange == textRange) {
            val declaration = comment.owner as? KtDeclaration ?: return null
            val modified = service.getModifiedElement(declaration)

            return modified?.let {
                DocProcessorInlineDocumentation(it.docComment as KDoc, modified as KtDeclaration)
            }
        }
        return null
    }
}
