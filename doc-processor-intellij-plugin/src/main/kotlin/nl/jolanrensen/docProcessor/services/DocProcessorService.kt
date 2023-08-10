package nl.jolanrensen.docProcessor.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import nl.jolanrensen.docProcessor.*
import nl.jolanrensen.docProcessor.defaultProcessors.*
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.idea.kdoc.resolveKDocLink
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import java.util.*

@Service(Service.Level.PROJECT)
class DocProcessorService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): DocProcessorService = project.service()
    }

    // TODO make configurable
    val processLimit: Int = 10_000

    /**
     * Determines whether the DocProcessor is enabled or disabled.
     */
    var isEnabled = true
        set(value) {
            field = value
            thisLogger().info(if (value) "DocProcessor enabled." else "DocProcessor disabled.")
        }

    /**
     * Helper function that queries the project for reference links and returns them as a list of DocumentableWrappers.
     */
    private fun query(context: PsiElement, link: String): List<DocumentableWrapper>? {
        val psiManager = PsiManager.getInstance(project)

        val descriptors = when (val navElement = context.navigationElement) {
            is KtElement -> {
                val resolutionFacade = navElement.getResolutionFacade()
                val bindingContext = navElement.safeAnalyzeNonSourceRootCode(resolutionFacade, BodyResolveMode.PARTIAL)
                val contextDescriptor =
                    bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, navElement] ?: return null

                resolveKDocLink(
                    context = bindingContext,
                    resolutionFacade = resolutionFacade,
                    fromDescriptor = contextDescriptor,
                    contextElement = navElement,
                    fromSubjectOfTag = null,
                    qualifiedName = link.split('.'),
                )
            }

            else -> error("Java not supported yet.")

        }

        val targets = descriptors
            .flatMap { DescriptorToSourceUtilsIde.getAllDeclarations(psiManager.project, it) }
            .map {
                when (it) {
                    is KtDeclaration, is PsiDocCommentOwner ->
                        DocumentableWrapper.createFromIntellijOrNull(it)

                    else -> null
                }
            }

        return when {
            // No declarations found in entire project, so null
            targets.isEmpty() -> null

            // All documentables are null, but still declarations found, so empty list
            targets.all { it == null } -> emptyList()

            else -> targets.filterNotNull()
        }
    }

    fun getModifiedElement(originalElement: PsiElement): PsiElement? {
        try {
            // Create a copy of the element, so we can modify it
            val psiElement = originalElement.copiedWithFile()

            // Create a DocumentableWrapper from the element
            val documentableWrapper = DocumentableWrapper.createFromIntellijOrNull(psiElement)
            if (documentableWrapper == null) {
                thisLogger().warn("Could not create DocumentableWrapper from element: $psiElement")
                return null
            }

            // Package the DocumentableWrapper in a DocumentablesByPath with the query function
            val docsToProcess = listOfNotNull(
                documentableWrapper.fullyQualifiedPath,
                documentableWrapper.fullyQualifiedExtensionPath,
            ).associateWith { listOf(documentableWrapper) }

            val documentablesByPath = DocumentablesByPathWithCache(
                unfilteredDocsToProcess = docsToProcess,
                query = { link -> query(psiElement, link) },
            )

            // Process the DocumentablesByPath
            val results = processDocumentablesByPath(documentablesByPath)

            // Retrieve the original DocumentableWrapper from the results
            val doc = results[documentableWrapper.identifier] ?: error("Something went wrong")

            // If the new doc is empty, delete the comment
            if (doc.docContent.isEmpty()) {
                psiElement.docComment?.delete()
                return psiElement
            }

            // If the new doc is not empty, generate a new doc element
            val newComment = when (doc.programmingLanguage) {
                ProgrammingLanguage.KOTLIN -> KDocElementFactory(project)
                    .createKDocFromText(
                        doc.docContent.toDoc()
                    )

                ProgrammingLanguage.JAVA -> PsiElementFactory.getInstance(project)
                    .createDocCommentFromText(
                        doc.docContent.toDoc()
                    )
            }

            // Replace the old doc element with the new one if it exists, otherwise add a new one
            if (psiElement.docComment != null) {
                psiElement.docComment?.replace(newComment)
            } else {
                psiElement.addBefore(newComment, psiElement.firstChild)
            }

            return psiElement
        } catch (e: Throwable) {
            e.printStackTrace()
            return null
        }
    }

    fun processDocumentablesByPath(sourceDocsByPath: DocumentablesByPath): DocumentablesByPath {
        // Find all processors
        Thread.currentThread().contextClassLoader = this.javaClass.classLoader
        val processors = findProcessors(
            fullyQualifiedNames = listOf(
                // TODO make customizable
                INCLUDE_DOC_PROCESSOR,
                INCLUDE_FILE_DOC_PROCESSOR,
                ARG_DOC_PROCESSOR,
                COMMENT_DOC_PROCESSOR,
                SAMPLE_DOC_PROCESSOR,
            ),
            arguments = mapOf(ARG_DOC_PROCESSOR_LOG_NOT_FOUND to false), // TODO
        )

        // Run all processors
        val modifiedDocumentables = processors.fold(sourceDocsByPath) { acc, processor ->
            processor.processSafely(processLimit = processLimit, documentablesByPath = acc)
        }

        return modifiedDocumentables
    }

    init {
        thisLogger().setLevel(LogLevel.INFO) // TEMP
        thisLogger().info(MessageBundle.message("projectService", project.name))
    }
}