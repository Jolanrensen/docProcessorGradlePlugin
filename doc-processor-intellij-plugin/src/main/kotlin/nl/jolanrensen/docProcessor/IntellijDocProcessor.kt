package nl.jolanrensen.docProcessor

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil.processElements
import nl.jolanrensen.docProcessor.defaultProcessors.*
import org.jetbrains.kotlin.idea.base.utils.fqname.getKotlinFqName
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import java.io.File
import java.util.*

class IntellijDocProcessor(private val project: Project, private val processLimit: Int = 10_000) {

    fun processFiles(psiFiles: List<PsiFile>) {
        println("processFiles: $psiFiles")

        val psiToDocumentableWrapper = mutableMapOf<PsiElement, UUID>()
        val sourceDocs: Map<String, List<DocumentableWrapper>> =
            getDocumentableWrappers(psiFiles) { wrapper, psiElement ->
                psiToDocumentableWrapper[psiElement] = wrapper.identifier
            }

        // Find all processors
        Thread.currentThread().contextClassLoader = this.javaClass.classLoader
        val processors = findProcessors(
            listOf(
                // TODO make customizable
                INCLUDE_DOC_PROCESSOR,
                INCLUDE_FILE_DOC_PROCESSOR,
                INCLUDE_ARG_DOC_PROCESSOR,
                COMMENT_DOC_PROCESSOR,
                SAMPLE_DOC_PROCESSOR,
            )
        )

        // Run all processors
        val modifiedDocumentables =
            processors.fold(sourceDocs) { acc, processor ->
                processor.processSafely(processLimit = processLimit, documentablesByPath = acc)
            }

        // filter to only include the modified documentables
        val modifiedDocumentablesPerFile = getModifiedDocumentablesPerFile(modifiedDocumentables)

        for (psiFile in psiFiles) {
            val file = File(psiFile.originalFile.virtualFile.path)
            val modified = modifiedDocumentablesPerFile[file]
                ?.associateBy { it.identifier }
                ?: continue

            processElements(psiFile) { psiElement ->
                if (psiElement !is KtDeclaration && psiElement !is PsiDocCommentOwner)
                    return@processElements true

                psiToDocumentableWrapper[psiElement]
                    ?.let { modified[it] }
                    ?.let { modifiedDocumentable ->
                        if (modifiedDocumentable.docContent.isEmpty()) {
                            psiElement.docComment?.delete()
                            return@let
                        }

                        val newComment = when (modifiedDocumentable.programmingLanguage) {
                            ProgrammingLanguage.KOTLIN -> KDocElementFactory(project)
                                .createKDocFromText(
                                    modifiedDocumentable.docContent.toDoc()
                                )

                            ProgrammingLanguage.JAVA -> PsiElementFactory.getInstance(project)
                                .createDocCommentFromText(
                                    modifiedDocumentable.docContent.toDoc()
                                )
                        }

                        if (psiElement.docComment != null) {
                            psiElement.docComment?.replace(newComment)
                        } else {
                            psiElement.addBefore(newComment, psiElement.firstChild)
                        }
                    }
                true
            }
        }
    }

    private fun getDocumentableWrappers(
        psiFiles: List<PsiFile>,
        reportDocumentable: (DocumentableWrapper, PsiElement) -> Unit
    ): Map<String, List<DocumentableWrapper>> {
        val allPaths = mutableSetOf<String>()
        val documentables = psiFiles.flatMap {
            val documentables = mutableListOf<PsiElement>()
            processElements(it) {
                if (it is KtDeclaration || it is PsiDocCommentOwner) {
                    documentables.add(it)
                } else {
                    it.getKotlinFqName()?.let { fqName ->
                        allPaths.add(fqName.asString())
                    }
                    if (it.isExtensionDeclaration()) {
                        // TODO
                        println("TODO: extension declaration")
                    }
                }
                true
            }

            documentables.mapNotNull { documentable ->
                DocumentableWrapper.createFromIntellijOrNull(documentable)
                    ?.also { reportDocumentable(it, documentable) }
            }
        }

        // collect the documentables with sources per path
        val documentablesPerPath: MutableMap<String, List<DocumentableWrapper>> = documentables
            .flatMap { doc ->
                listOfNotNull(doc.fullyQualifiedPath, doc.fullyQualifiedExtensionPath).map { it to doc }
            }
            .groupBy { it.first }
            .mapValues { it.value.map { it.second } }
            .toMutableMap()

        // add the paths for documentables without sources to the map
        for (path in allPaths) {
            if (path !in documentablesPerPath) {
                documentablesPerPath[path] = emptyList()
            }
        }

        return documentablesPerPath
    }

    private fun getModifiedDocumentablesPerFile(
        modifiedSourceDocs: Map<String, List<DocumentableWrapper>>,
    ): Map<File, List<DocumentableWrapper>> =
        modifiedSourceDocs
            .entries
            .flatMap {
                it.value.filter {
                    it.isModified // filter out unmodified documentables
                }
            }
            .groupBy { it.file }
}