package nl.jolanrensen.docProcessor.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil.processElements
import com.intellij.psi.util.elementType
import nl.jolanrensen.docProcessor.MessageBundle
import nl.jolanrensen.docProcessor.IntellijDocProcessor
import nl.jolanrensen.docProcessor.listeners.DocProcessorFileListener
import nl.jolanrensen.docProcessor.toDoc
import org.jetbrains.kotlin.idea.base.utils.fqname.getKotlinFqName
import org.jetbrains.kotlin.idea.core.copied
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.extensions.gradle.getTopLevelBuildScriptPsiFile
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.idea.util.isJavaFileType
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.psi.KtDeclaration
import java.util.*

@Service(Service.Level.PROJECT)
class DocProcessorService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): DocProcessorService = project.service()

        fun getModifiedFilesByPath(project: Project): Map<String, PsiFile> =
            CachedValuesManager.getProjectPsiDependentCache(project.getTopLevelBuildScriptPsiFile()!!) {
                val psiList = indexProject(project)
                IntellijDocProcessor(project).processFiles(psiList)
                psiList.associateBy { it.originalFile.virtualFile.path }
            }

        private fun indexProject(project: Project): List<PsiFile> {
            println("Indexing project: ${project.name} started")

            val docProcessorFileListener = DocProcessorFileListener(project).also {
                project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, it)
            }

            val result = mutableListOf<PsiFile>()
            var successful = false
            while (!successful) {
                result.clear()
                val startPsiValue = docProcessorFileListener.getChangeCount()
                successful = ProjectFileIndex.SERVICE.getInstance(project).iterateContent(
                    /* processor = */
                    {
                        if (docProcessorFileListener.getChangeCount() != startPsiValue) {
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
                        (it.isKotlinFileType() || it.isJavaFileType()) && it.exists()
                    },
                )
            }

            return result
        }
    }

    private fun PsiElement.getIndexInParent(): Int = parent.children.indexOf(this)

    fun getModifiedElement(element: PsiElement): PsiElement? {
        val modifiedFile = getModifiedFile(element.containingFile) ?: return null
        var result: PsiElement? = null
        processElements(modifiedFile) {
            if (it.elementType == element.elementType &&
                it.getKotlinFqName() == element.getKotlinFqName() &&
                it.getIndexInParent() == element.getIndexInParent()
            ) {
                result = it
                false
            } else true
        }

        return result
    }

    private fun getModifiedFile(file: PsiFile): PsiFile? = file.originalFile.virtualFile?.path?.let {
        getModifiedFilesByPath(project)[it]
    }


    init {
        thisLogger().setLevel(LogLevel.INFO) // TEMP
        thisLogger().info(MessageBundle.message("projectService", project.name))
    }

    private fun getModificationCount(): Long = PsiModificationTracker.SERVICE.getInstance(project).modificationCount



    fun getRandomNumber() = (1..100).random()
}