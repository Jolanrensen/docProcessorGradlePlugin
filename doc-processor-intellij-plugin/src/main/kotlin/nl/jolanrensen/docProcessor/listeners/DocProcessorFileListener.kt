package nl.jolanrensen.docProcessor.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import org.jetbrains.kotlin.idea.util.isJavaFileType
import org.jetbrains.kotlin.idea.util.isKotlinFileType

class DocProcessorFileListener(private val project: Project) : BulkFileListener {


    private var fileChangeCounter: Long = 0L
    private var contentChangeCounter: Long = 0L

    fun getFileChangeCount(): Long = fileChangeCounter

    fun getContentChangeCount(): Long = contentChangeCounter + fileChangeCounter

    override fun after(events: MutableList<out VFileEvent>) {
        val fileIndex = ProjectFileIndex.getInstance(project)
        for (event in events) {
            when (event) {
                is VFileCopyEvent,
                is VFileMoveEvent,
                is VFileCreateEvent -> {
                    event.file?.let { file ->
                        if ((file.isKotlinFileType() || file.isJavaFileType()) && // TODO
                            fileIndex.isInContent(file)
                        ) {
                            fileChangeCounter += 1L
                            println("file change detected: ${file.path}")
                        }
                    }
                }

                is VFileDeleteEvent -> {
                    fileChangeCounter += 1L
                    println("file deleted detected: ${event.path}")
                }

                is VFileContentChangeEvent -> {
                    if ((event.file.isKotlinFileType() || event.file.isJavaFileType()) && // TODO
                        fileIndex.isInContent(event.file)
                    ) {
                        contentChangeCounter += 1L
                        println("content change detected: ${event.file.path}")
                    }

                }
            }
        }
    }
}