@file:OptIn(ExperimentalContracts::class)

package nl.jolanrensen.docProcessor.documentationProvider

import com.intellij.codeInsight.javadoc.JavaDocExternalFilter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.platform.backend.documentation.InlineDocumentationProvider
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.startOffset
import nl.jolanrensen.docProcessor.docComment
import nl.jolanrensen.docProcessor.services.DocProcessorServiceK2
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinInlineDocumentationProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinPsiDocumentationTargetProvider
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.renderKDoc
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import kotlin.contracts.ExperimentalContracts

/**
 * K2
 *
 * check out [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinDocumentationLinkHandler] and
 * [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.resolveKDocLink]
 */

// by element, used on hover and Ctrl+Q
class DocProcessorPsiDocumentationTargetProvider : PsiDocumentationTargetProvider {

    /**
     * Creates [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinPsiDocumentationTargetProvider]
     */
    private val kotlinPsi = KotlinPsiDocumentationTargetProvider()

    private val serviceInstances: MutableMap<Project, DocProcessorServiceK2> = mutableMapOf()

    private fun getService(project: Project) =
        serviceInstances.getOrPut(project) { DocProcessorServiceK2.getInstance(project) }

    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
        val service = getService(element.project)
        if (!service.isEnabled) return kotlinPsi.documentationTarget(element, originalElement)
//        println("DocProcessorPsiDocumentationTargetProvider.documentationTarget($element, $originalElement)")

        val modifiedElement = service.getModifiedElement(element)
        return try {
            kotlinPsi.documentationTarget(modifiedElement ?: element, originalElement)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// by offset in a file TODO not needed?
// class DocProcessorDocumentationTargetProvider : DocumentationTargetProvider {
//
//    fun PsiElement?.isModifier(): Boolean {
//        contract { returns(true) implies (this@isModifier != null) }
//        return this != null &&
//            parent is KtModifierList &&
//            KtTokens.MODIFIER_KEYWORDS_ARRAY.firstOrNull { it.value == text } != null
//    }
//
//    /**
//     * Creates [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinDocumentationTarget]
//     */
//    val kotlin = KotlinDocumentationTargetProvider()
//
//    private val serviceInstances: MutableMap<Project, DocProcessorServiceK2> = mutableMapOf()
//
//    private fun getService(project: Project) =
//        serviceInstances.getOrPut(project) { DocProcessorServiceK2.getInstance(project) }
//
//    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
//        val service = getService(file.project)
//        if (!service.isEnabled) return kotlin.documentationTargets(file, offset)
//        println("DocProcessorDocumentationTargetProvider.documentationTargets($file, $offset)")
//
//        val element = file.findElementAt(offset)
//        if (!element.isModifier()) return emptyList()
//
//        val modifiedElement = service.getModifiedElement(element)
//        if (modifiedElement == null) return emptyList()
//
//        val modifiedFile = modifiedElement.containingFile ?: return emptyList()
//
//        return try {
//            kotlin.documentationTargets(modifiedFile, modifiedElement.startOffset)
//        } catch (e: Exception) {
//            e.printStackTrace()
//            emptyList()
//        }
//    }
// }

// inline, used for rendering single doc comment in file, does not work for multiple?
@ApiStatus.Experimental
class DocProcessorInlineDocumentationProvider : InlineDocumentationProvider {

    class DocProcessorInlineDocumentation(
        private val originalDocumentation: PsiDocCommentBase,
        private val originalOwner: KtDeclaration,
        private val modifiedDocumentation: PsiDocCommentBase,
        private val modifiedOwner: KtDeclaration,
    ) : InlineDocumentation {

        override fun getDocumentationRange(): TextRange = originalDocumentation.textRange

        override fun getDocumentationOwnerRange(): TextRange? = originalOwner.textRange

        override fun renderText(): String? {
            modifiedDocumentation as? KDoc ?: return null
            val result = buildString {
                renderKDoc(
                    contentTag = modifiedDocumentation.getDefaultSection(),
                    sections = modifiedDocumentation.getAllSections(),
                )
            }
            return JavaDocExternalFilter.filterInternalDocInfo(result)
        }

        override fun getOwnerTarget(): DocumentationTarget? {
            val kotlinDocumentationTargetClass = Class.forName(
                "org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinDocumentationTarget",
            )
            val constructor = kotlinDocumentationTargetClass.constructors.first()
            constructor.isAccessible = true
            val target = constructor.newInstance(originalOwner, originalOwner) as DocumentationTarget
            return target
        }
    }

    private val serviceInstances: MutableMap<Project, DocProcessorServiceK2> = mutableMapOf()

    private fun getService(project: Project) =
        serviceInstances.getOrPut(project) { DocProcessorServiceK2.getInstance(project) }

    /**
     * Creates [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinInlineDocumentation]
     */
    val kotlin = KotlinInlineDocumentationProvider()

    // TODO works but is somehow overridden by CompatibilityInlineDocumentationProvider...
    override fun inlineDocumentationItems(file: PsiFile?): Collection<InlineDocumentation> {
        if (file !is KtFile) return emptyList()

        val service = getService(file.project)
        if (!service.isEnabled) return kotlin.inlineDocumentationItems(file)

//        logger<DocProcessorInlineDocumentationProvider>().warn(
//            """
//            |The doc preprocessor does not work for rendering all doc comments in a file.
//            |You can, however, render them all individually.
//            """.trimMargin(),
//        )

        val result = mutableListOf<InlineDocumentation>()
        PsiTreeUtil.processElements(file) {
            val owner = it as? KtDeclaration ?: return@processElements true
            val originalDocumentation = owner.docComment as KDoc? ?: return@processElements true
            result += findInlineDocumentation(file, originalDocumentation.textRange) ?: return@processElements true

            true
        }

        return result.also {
//            println("DocProcessorInlineDocumentationProvider.inlineDocumentationItems($file) -> $it")
        }
    }

    // todo works!
    override fun findInlineDocumentation(file: PsiFile, textRange: TextRange): InlineDocumentation? {
        val service = getService(file.project)
        if (!service.isEnabled) return kotlin.findInlineDocumentation(file, textRange)
//        println("DocProcessorInlineDocumentationProvider.findInlineDocumentation($file, $textRange)")

        val comment = PsiTreeUtil.getParentOfType(
            file.findElementAt(textRange.startOffset),
            PsiDocCommentBase::class.java,
        ) ?: return null

        if (comment.textRange != textRange) return null

        val declaration = comment.owner as? KtDeclaration ?: return null
        val modified = service.getModifiedElement(declaration)

        if (modified == null) return null

        return DocProcessorInlineDocumentation(
            originalDocumentation = declaration.docComment as KDoc,
            originalOwner = declaration,
            modifiedDocumentation = modified.docComment as KDoc,
            modifiedOwner = modified as KtDeclaration,
        )
    }
}
