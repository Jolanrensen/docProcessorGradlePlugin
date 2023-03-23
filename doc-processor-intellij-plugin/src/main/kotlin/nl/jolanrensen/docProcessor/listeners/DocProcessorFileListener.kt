package nl.jolanrensen.docProcessor.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*

class DocProcessorFileListener(private val project: Project) : BulkFileListener {


    private var fileChangeCounter: Long = 0L

    fun getChangeCount(): Long = fileChangeCounter

    override fun after(events: MutableList<out VFileEvent>) {
        val fileIndex = ProjectFileIndex.getInstance(project)
        for (event in events) {
            when (event) {
                is VFileCopyEvent,
                is VFileMoveEvent,
                is VFileCreateEvent -> {
                    event.file?.let { file ->
                        if (fileIndex.isInContent(file)) {
                            fileChangeCounter += 1L
                            println("file change detected: ${file.path}")
                        }
                    }
                }

                is VFileDeleteEvent -> {
                    fileChangeCounter += 1L
                    println("file deleted detected: ${event.path}")
                }
            }
        }
    }
}