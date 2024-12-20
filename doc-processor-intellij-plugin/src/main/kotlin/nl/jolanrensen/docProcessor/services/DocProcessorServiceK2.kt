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
import com.intellij.psi.PsiPolyVariantReference
import nl.jolanrensen.docProcessor.DocContent
import nl.jolanrensen.docProcessor.DocumentableWrapper
import nl.jolanrensen.docProcessor.DocumentablesByPath
import nl.jolanrensen.docProcessor.DocumentablesByPathWithCache
import nl.jolanrensen.docProcessor.MessageBundle
import nl.jolanrensen.docProcessor.Mode
import nl.jolanrensen.docProcessor.ProgrammingLanguage
import nl.jolanrensen.docProcessor.TagDocProcessorFailedException
import nl.jolanrensen.docProcessor.asDocContent
import nl.jolanrensen.docProcessor.copiedWithFile
import nl.jolanrensen.docProcessor.createFromIntellijOrNull
import nl.jolanrensen.docProcessor.docComment
import nl.jolanrensen.docProcessor.docProcessorIsEnabled
import nl.jolanrensen.docProcessor.getLoadedProcessors
import nl.jolanrensen.docProcessor.getOrigin
import nl.jolanrensen.docProcessor.preprocessorMode
import nl.jolanrensen.docProcessor.programmingLanguage
import nl.jolanrensen.docProcessor.toDocText
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import java.util.concurrent.CancellationException
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Service(Service.Level.PROJECT)
class DocProcessorServiceK2(private val project: Project) {

    /**
     * See [DocProcessorServiceK2]
     */
    private val logger = logger<DocProcessorServiceK2>()

    companion object {
        fun getInstance(project: Project): DocProcessorServiceK2 = project.service()
    }

    // TODO make configurable
    val processLimit: Int = 10_000

    /**
     * Determines whether the DocProcessor is enabled or disabled.
     */
    val isEnabled get() = docProcessorIsEnabled && preprocessorMode == Mode.K2

    fun PsiElement.allChildren(): List<PsiElement> = children.toList() + children.flatMap { it.allChildren() }

    fun PsiElement.allChildrenOfType(kType: KType): List<PsiElement> =
        children.filter { it::class == kType.classifier } +
            children.flatMap { it.allChildrenOfType(kType) }

    /**
     * Resolves a KDoc link from a context by copying the context, adding
     * a new KDoc with just the link there, then resolve it, finally restoring the original.
     */
    private fun resolveKDocLink(link: String, context: PsiElement): List<PsiElement> {
        // Create a copy of the element, so we can modify it
        val psiElement = try {
            context.copiedWithFile()
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        val originalComment = psiElement.docComment

        try {
            val newComment = KDocElementFactory(project)
                .createKDocFromText("/**[$link]*/")

            val newCommentInContext =
                if (psiElement.docComment != null) {
                    psiElement.docComment!!.replace(newComment)
                } else {
                    psiElement.addBefore(newComment, psiElement.firstChild)
                }

            // KDocLink is `[X.Y]`, KDocNames are X, X.Y, for some reason
            return newCommentInContext
                .allChildrenOfType(typeOf<KDocName>())
                .maxBy { it.text.length }
                .let {
                    when (val ref = it.reference) {
                        is PsiPolyVariantReference -> ref.multiResolve(false).mapNotNull { it?.element }
                        else -> listOfNotNull(ref?.resolve())
                    }
                }
        } catch (e: Exception) {
            return emptyList()
        } finally {
            // restore the original docComment state so the text range is still correct
            if (originalComment == null) {
                psiElement.docComment!!.delete()
            } else {
                psiElement.docComment!!.replace(originalComment.copied())
            }
        }
    }

    /**
     * Helper function that queries the project for reference links and returns them as a list of DocumentableWrappers.
     */
    private fun query(context: PsiElement, link: String): List<DocumentableWrapper>? {
        logger.debug { "querying intellij for: $link, from ${(context.navigationElement as? KtElement)?.name}" }

        val kaSymbols = when (val navElement = context.navigationElement) {
            is KtElement -> {
                resolveKDocLink(
                    link = link,
                    context = navElement,
                )
            }

            else -> error("Java not supported yet.")
        }

        val targets = kaSymbols.map {
            when (it) {
                is KtDeclaration, is PsiDocCommentOwner ->
                    DocumentableWrapper.createFromIntellijOrNull(it, useK2 = true)

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

        val newDocContent = getProcessedDocContent(psiElement) ?: return null

        // If the new doc is empty, delete the comment
        if (newDocContent.value.isEmpty()) {
            psiElement.docComment?.delete()
            return psiElement
        }

        // If the new doc is not empty, generate a new doc element
        val newComment = try {
            when (originalElement.programmingLanguage) {
                ProgrammingLanguage.KOTLIN ->
                    KDocElementFactory(project)
                        .createKDocFromText(newDocContent.toDocText().value)

                // TODO can crash here?

                ProgrammingLanguage.JAVA ->
                    PsiElementFactory.getInstance(project)
                        .createDocCommentFromText(newDocContent.toDocText().value)
            }
        } catch (_: Exception) {
            return null
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

    fun getDocumentableWrapperOrNull(psiElement: PsiElement): DocumentableWrapper? {
        val documentableWrapper = DocumentableWrapper.createFromIntellijOrNull(psiElement, useK2 = true)
        if (documentableWrapper == null) {
            thisLogger().warn("Could not create DocumentableWrapper from element: $psiElement")
        }
        return documentableWrapper
    }

    /**
     * Returns a processed version of the DocumentableWrapper, or `null` if it could not be processed.
     * ([DocumentableWrapper.docContent] contains the modified doc content).
     */
    fun getProcessedDocumentableWrapperOrNull(documentableWrapper: DocumentableWrapper): DocumentableWrapper? {
        val needsRebuild = documentableCache.updatePreProcessing(documentableWrapper)

        logger.debug { "\n\n" }

        if (!needsRebuild) {
            logger.debug {
                "loading fully cached ${
                    documentableWrapper.fullyQualifiedPath
                }/${documentableWrapper.fullyQualifiedExtensionPath}"
            }

            val docContentFromCache = documentableCache.getDocContentResult(documentableWrapper.identifier)

            // should never be null, but just in case
            if (docContentFromCache != null) {
                return documentableWrapper.copy(
                    docContent = docContentFromCache,
                    tags = emptySet(),
                    isModified = true,
                )
            }
        }
        logger.debug {
            "preprocessing ${
                documentableWrapper.fullyQualifiedPath
            }/${documentableWrapper.fullyQualifiedExtensionPath}"
        }

        // Process the DocumentablesByPath
        val results = processDocumentablesByPath(documentableCache)

        // Retrieve the original DocumentableWrapper from the results
        val doc = results[documentableWrapper.identifier] ?: return null

        documentableCache.updatePostProcessing()

        return doc
    }

    private fun getProcessedDocContent(psiElement: PsiElement): DocContent? {
        return try {
            // Create a DocumentableWrapper from the element
            val documentableWrapper = getDocumentableWrapperOrNull(psiElement)
                ?: return null

            // get the processed version of the DocumentableWrapper
            val processed = getProcessedDocumentableWrapperOrNull(documentableWrapper)
                ?: return null

            processed.docContent
        } catch (_: ProcessCanceledException) {
            return null
        } catch (_: CancellationException) {
            return null
        } catch (e: TagDocProcessorFailedException) {
//            println(e.message)
//            println(e.cause)
            // e.printStackTrace()
            // render fancy :)
            e.renderDoc()
        } catch (e: Throwable) {
//            println(e.message)
//            println(e.cause)
            // e.printStackTrace()

            // instead of throwing the exception, render it inside the kdoc
            """
            |```
            |$e
            |
            |${e.stackTrace.joinToString("\n")}
            |```
            """.trimMargin().asDocContent()
        }
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
