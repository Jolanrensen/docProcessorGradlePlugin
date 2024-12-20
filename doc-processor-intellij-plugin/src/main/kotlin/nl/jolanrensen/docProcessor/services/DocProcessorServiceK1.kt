package nl.jolanrensen.docProcessor.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiManager
import nl.jolanrensen.docProcessor.DocContent
import nl.jolanrensen.docProcessor.DocumentableWrapper
import nl.jolanrensen.docProcessor.DocumentablesByPath
import nl.jolanrensen.docProcessor.DocumentablesByPathWithCache
import nl.jolanrensen.docProcessor.ExportAsHtml
import nl.jolanrensen.docProcessor.MessageBundle
import nl.jolanrensen.docProcessor.Mode
import nl.jolanrensen.docProcessor.ProgrammingLanguage
import nl.jolanrensen.docProcessor.TagDocProcessorFailedException
import nl.jolanrensen.docProcessor.annotationNames
import nl.jolanrensen.docProcessor.asDocContent
import nl.jolanrensen.docProcessor.copiedWithFile
import nl.jolanrensen.docProcessor.createFromIntellijOrNull
import nl.jolanrensen.docProcessor.docComment
import nl.jolanrensen.docProcessor.docProcessorIsEnabled
import nl.jolanrensen.docProcessor.getLoadedProcessors
import nl.jolanrensen.docProcessor.getOrigin
import nl.jolanrensen.docProcessor.preprocessorMode
import nl.jolanrensen.docProcessor.programmingLanguage
import nl.jolanrensen.docProcessor.renderToHtml
import nl.jolanrensen.docProcessor.toDocText
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.idea.kdoc.resolveKDocLink
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getValueArgumentsInParentheses
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import java.io.File
import java.util.concurrent.CancellationException

/**
 * See also [DocProcessorServiceK2]
 */
@Suppress("UnstableApiUsage")
@Service(Service.Level.PROJECT)
class DocProcessorServiceK1(private val project: Project) {

    private val logger = logger<DocProcessorServiceK1>()

    companion object {
        fun getInstance(project: Project): DocProcessorServiceK1 = project.service()
    }

    // TODO make configurable
    val processLimit: Int = 10_000

    /**
     * Determines whether the DocProcessor is enabled or disabled.
     */
    val isEnabled get() = docProcessorIsEnabled && preprocessorMode == Mode.K1

    /**
     * Helper function that queries the project for reference links and returns them as a list of DocumentableWrappers.
     */
    private fun query(context: PsiElement, link: String): List<DocumentableWrapper>? {
        logger.debug { "querying intellij for: $link, from ${(context.navigationElement as? KtElement)?.name}" }
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
                        DocumentableWrapper.createFromIntellijOrNull(it, useK2 = false)

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

    /**
     * Returns a copy of the element with the doc comment modified. If the doc comment is empty, it will be deleted.
     * If it didn't exist before, it will be created anew. Return `null` means it could not be modified and the original
     * rendering method should be used.
     */
    @Synchronized
    fun getModifiedElement(originalElement: PsiElement): PsiElement? {
        // Create a copy of the element, so we can modify it
        val psiElement = try {
            originalElement.copiedWithFile()
        } catch (e: Exception) {
            null
        } ?: return null

        // must have the ability to own a docComment
        try {
            psiElement.docComment
        } catch (e: IllegalStateException) {
            return null
        }

        val newDocContent = getDocContent(psiElement) ?: return null

        // If the new doc is empty, delete the comment
        if (newDocContent.value.isEmpty()) {
            psiElement.docComment?.delete()
            return psiElement
        }

        // If the new doc is not empty, generate a new doc element
        val newComment = when (originalElement.programmingLanguage) {
            ProgrammingLanguage.KOTLIN ->
                KDocElementFactory(project)
                    .createKDocFromText(newDocContent.toDocText().value)

            ProgrammingLanguage.JAVA ->
                PsiElementFactory.getInstance(project)
                    .createDocCommentFromText(newDocContent.toDocText().value)
        }

        // Replace the old doc element with the new one if it exists, otherwise add a new one
        if (psiElement.docComment != null) {
            psiElement.docComment?.replace(newComment)
        } else {
            psiElement.addBefore(newComment, psiElement.firstChild)
        }

        return psiElement
    }

    private val documentableCache = DocumentablesByPathWithCache(
        processLimit = processLimit,
        logDebug = { logger.debug(null, it) },
        queryNew = { context, link ->
            query(context.getOrigin(), link)
        },
    )

    private fun getDocContent(psiElement: PsiElement): DocContent? {
        return try {
            // Create a DocumentableWrapper from the element
            val documentableWrapper = DocumentableWrapper.createFromIntellijOrNull(psiElement, useK2 = false)
            if (documentableWrapper == null) {
                thisLogger().warn("Could not create DocumentableWrapper from element: $psiElement")
                return null
            }
            val needsRebuild = documentableCache.updatePreProcessing(documentableWrapper)

            logger.debug { "\n\n" }

            if (!needsRebuild) {
                logger.debug {
                    "loading fully cached ${
                        documentableWrapper.fullyQualifiedPath
                    }/${documentableWrapper.fullyQualifiedExtensionPath}"
                }
                return documentableCache.getDocContentResult(documentableWrapper.identifier)!!
            }
            logger.debug {
                "preprocessing ${
                    documentableWrapper.fullyQualifiedPath
                }/${documentableWrapper.fullyQualifiedExtensionPath}"
            }

            // Process the DocumentablesByPath
            val results = processDocumentablesByPath(documentableCache)

            // Retrieve the original DocumentableWrapper from the results
            val doc = results[documentableWrapper.identifier] ?: return null // error("Something went wrong")

            documentableCache.updatePostProcessing()

            // TODO replace with doc.annotations
            val hasExportAsHtmlTag = psiElement.annotationNames.any {
                ExportAsHtml::class.simpleName!! in it
            }

            if (hasExportAsHtmlTag) {
                val file = exportToHtmlFile(psiElement, doc)
                (doc.docContent.value + "\n\n" + "Exported HTML: [${file.name}](file://${file.absolutePath})")
                    .asDocContent()
            } else {
                doc.docContent
            }
        } catch (e: ProcessCanceledException) {
            return null
        } catch (e: CancellationException) {
            return null
        } catch (e: TagDocProcessorFailedException) {
//            e.printStackTrace()
            // render fancy :)
            e.renderDoc()
        } catch (e: Throwable) {
//            e.printStackTrace()

            // instead of throwing the exception, render it inside the kdoc
            "```\n$e\n```".asDocContent()
        }
    }

    private fun exportToHtmlFile(psiElement: PsiElement, doc: DocumentableWrapper): File {
        val annotationArgs = (psiElement as? KtDeclaration)
            ?.annotationEntries
            ?.firstOrNull { ExportAsHtml::class.simpleName!! in it.shortName!!.asString() }
            ?.getValueArgumentsInParentheses()
            ?: emptyList()

        val themeArg = annotationArgs.firstOrNull {
            it.getArgumentName()?.asName?.toString() == ExportAsHtml::theme.name
        } ?: annotationArgs.getOrNull(0)?.takeIf {
            !it.isNamed() && it.getArgumentExpression()?.text?.toBoolean() != null
        }
        val theme = themeArg?.getArgumentExpression()?.text?.toBoolean() ?: true

        val stripReferencesArg = annotationArgs.firstOrNull {
            it.getArgumentName()?.asName?.toString() == ExportAsHtml::stripReferences.name
        } ?: annotationArgs.getOrNull(1)?.takeIf {
            !it.isNamed() && it.getArgumentExpression()?.text?.toBoolean() != null
        }
        val stripReferences = stripReferencesArg?.getArgumentExpression()?.text?.toBoolean() ?: true

        val html = doc
            .getDocContentForHtmlRange()
            .renderToHtml(theme = theme, stripReferences = stripReferences)
        val file = File.createTempFile(doc.fullyQualifiedPath, ".html")
        file.writeText(html)

        return file
    }

    private fun processDocumentablesByPath(sourceDocsByPath: DocumentablesByPath): DocumentablesByPath {
        // Find all processors
        val processors = getLoadedProcessors().toMutableList()

        // for cache collecting after include doc processor
        processors.add(1, PostIncludeDocProcessorCacheCollector(documentableCache))

        // Run all processors
        val modifiedDocumentables = processors.fold(sourceDocsByPath) { acc, processor ->
            processor.processSafely(processLimit = processLimit, documentablesByPath = acc)
        }

        return modifiedDocumentables
    }

    init {
        thisLogger().setLevel(LogLevel.INFO) // TEMP
    }
}
