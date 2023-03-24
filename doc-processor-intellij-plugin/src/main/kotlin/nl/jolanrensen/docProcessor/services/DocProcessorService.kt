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
import com.intellij.psi.util.PsiTreeUtil.processElements
import com.intellij.psi.util.elementType
import nl.jolanrensen.docProcessor.IntellijDocProcessor
import nl.jolanrensen.docProcessor.MessageBundle
import nl.jolanrensen.docProcessor.listeners.DocProcessorFileListener
import org.jetbrains.kotlin.idea.base.utils.fqname.getKotlinFqName
import org.jetbrains.kotlin.idea.core.copied
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.util.isJavaFileType
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service(Service.Level.PROJECT)
class DocProcessorService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): DocProcessorService = project.service()
    }

    // make sure to listen to file changes
    private val docProcessorFileListener = DocProcessorFileListener(project).also {
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, it)
    }

    private var modifiedFilesByPathCache: Map<String, PsiFile> = emptyMap()
    private var contentChangeCount: Long = Long.MIN_VALUE

    private val lock = ReentrantLock()

//    @Synchronized
    private fun getModifiedFilesByPath(): Map<String, PsiFile> { // TODO improve stability
        return lock.withLock {
            val currentContentChangeCount = docProcessorFileListener.getContentChangeCount()
            if (currentContentChangeCount != contentChangeCount) {
                contentChangeCount = currentContentChangeCount
                val psiList = indexProject()
                IntellijDocProcessor(project).processFiles(psiList)
                modifiedFilesByPathCache = psiList.associateBy { it.originalFile.virtualFile.path }
            }

            modifiedFilesByPathCache
        }
    }

    private fun indexProject(): List<PsiFile> {
        println("Indexing project: ${project.name} started")

        val result = mutableListOf<PsiFile>()
        var successful = false
        while (!successful) {
            result.clear()
            val startPsiValue = docProcessorFileListener.getFileChangeCount()
            successful = ProjectFileIndex.SERVICE.getInstance(project).iterateContent(
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

    private fun getModifiedFile(file: PsiFile?): PsiFile? = file?.originalFile?.virtualFile?.path?.let {
        getModifiedFilesByPath()[it]
    }

    init {
        thisLogger().setLevel(LogLevel.INFO) // TEMP
        thisLogger().info(MessageBundle.message("projectService", project.name))
    }

    fun getRandomNumber() = (1..100).random()
}