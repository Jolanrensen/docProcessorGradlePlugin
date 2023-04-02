package nl.jolanrensen.docProcessor.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil.processElements
import com.intellij.psi.util.elementType
import nl.jolanrensen.docProcessor.*
import nl.jolanrensen.docProcessor.listeners.DocProcessorFileListener
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.idea.kdoc.resolveKDocLink
import org.jetbrains.kotlin.idea.util.getValue
import org.jetbrains.kotlin.idea.util.isJavaFileType
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.idea.util.psiModificationTrackerBasedCachedValue
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import java.util.*

@Service(Service.Level.PROJECT)
class DocProcessorService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): DocProcessorService = project.service()
    }

    // make sure to listen to file changes
    private val docProcessorFileListener = DocProcessorFileListener(project)
        .also {
            project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, it)
        }

    private val modifiedFilesByPath by psiModificationTrackerBasedCachedValue(project) {
        val psiList = indexProject()
        IntellijDocProcessor(project).processFiles(psiList)
        psiList.associateBy { it.originalFile.virtualFile.path }
    }

    private fun indexProject(): List<PsiFile> {
        println("Indexing project: ${project.name} started")

        val result = mutableListOf<PsiFile>()
        var successful = false
        while (!successful) {
            result.clear()
            val startPsiValue = docProcessorFileListener.getFileChangeCount()
            successful = ProjectFileIndex.getInstance(project).iterateContent(
                /* processor = */
                {
                    if (docProcessorFileListener.getFileChangeCount() != startPsiValue) {
                        thisLogger().info("Indexing project: ${project.name} cancelled")
                        false
                    } else {
                        it.toPsiFile(project)?.let { psiFile ->
                            result += psiFile.copied()
                        }
                        true
                    }
                },
                /* filter = */
                {
                    "build/generated" !in it.path && // TODO make this configurable
                            it.exists() &&
                            (it.isKotlinFileType() || it.isJavaFileType())
                },
            )
        }

        return result
    }

    private val PsiElement.indexInParent: Int
        get() = parent.children.indexOf(this)

    private fun query(context: PsiElement, link: String): List<DocumentableWrapper>? {
        val psiManager = PsiManager.getInstance(project)

        val navElement = context.navigationElement as? KtElement ?: return null // TODO support java
        val resolutionFacade = navElement.getResolutionFacade()
        val bindingContext = navElement.safeAnalyzeNonSourceRootCode(resolutionFacade, BodyResolveMode.PARTIAL)
        val contextDescriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, navElement] ?: return null
        val descriptors = resolveKDocLink(
            bindingContext, resolutionFacade,
            contextDescriptor, navElement, null, link.split('.')
        )

        val targets = descriptors.flatMap {
            DescriptorToSourceUtilsIde.getAllDeclarations(psiManager.project, it)
        }.mapNotNull {
            DocumentableWrapper.createFromIntellijOrNull(it)
        }

        return targets
    }

    fun getModifiedElement(
        originalElement: PsiElement,
    ): PsiElement? {
        // TODO get element from copied file
        val file = originalElement.containingFile.copied()


        var result: PsiElement? = null
        processElements(file) {
            if (it.elementType == originalElement.elementType &&
                it.kotlinFqName == originalElement.kotlinFqName &&
                it.indexInParent == originalElement.indexInParent
            ) {
                result = it
                false
            } else true
        }

        val psiElement = result ?: return null

//        val psiElement =
//            file.findUElementAt(originalElement.textOffset, KtDeclaration::class.java)
//
//                ?: return null

        val documentableWrapper = DocumentableWrapper.createFromIntellijOrNull(psiElement)
        if (documentableWrapper == null) {
            thisLogger().warn("Could not create DocumentableWrapper from element: $psiElement")
            return null
        }

        val docsToProcess = listOfNotNull(
            documentableWrapper.fullyQualifiedPath,
            documentableWrapper.fullyQualifiedExtensionPath,
        ).associateWith { listOf(documentableWrapper) }

        val documentablesByPath = DocumentablesByPathWithCache(
            unfilteredDocsToProcess = docsToProcess,
            query = { link ->
                query(psiElement, link)
            },
        )

        val results = IntellijDocProcessor(project)
            .processDocumentablesByPath(documentablesByPath)

        val doc = listOfNotNull(
            documentableWrapper.fullyQualifiedPath,
            documentableWrapper.fullyQualifiedExtensionPath,
        )
            .firstNotNullOfOrNull { results[it] }
            .let { it ?: error("") }
            .first { it.identifier == documentableWrapper.identifier }

        if (doc.docContent.isEmpty()) {
            psiElement.docComment?.delete()
            return psiElement
        }

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

        if (psiElement.docComment != null) {
            psiElement.docComment?.replace(newComment)
        } else {
            psiElement.addBefore(newComment, psiElement.firstChild)
        }

        return psiElement


//        val modifiedFile = getModifiedFile(psiElement.containingFile) ?: return null
//        var result: PsiElement? = null
//        processElements(modifiedFile) {
//            if (it.elementType == psiElement.elementType &&
//                it.kotlinFqName == psiElement.kotlinFqName &&
//                it.indexInParent == psiElement.indexInParent
//            ) {
//                result = it
//                false
//            } else true
//        }
//
//        return result
    }

    private fun getModifiedFile(file: PsiFile?): PsiFile? = file?.originalFile?.virtualFile?.path?.let {
        modifiedFilesByPath[it]
    }

    init {
        thisLogger().setLevel(LogLevel.INFO) // TEMP
        thisLogger().info(MessageBundle.message("projectService", project.name))
    }

    fun getRandomNumber() = (1..100).random()
}