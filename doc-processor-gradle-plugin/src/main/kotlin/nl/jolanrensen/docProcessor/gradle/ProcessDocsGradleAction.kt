package nl.jolanrensen.docProcessor.gradle

import nl.jolanrensen.docProcessor.ProcessDocsAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.jetbrains.dokka.DokkaSourceSetImpl
import java.io.File

/**
 * Process docs gradle action.
 *
 * Gradle wrapper for [ProcessDocsAction].
 */
abstract class ProcessDocsGradleAction :
    ProcessDocsAction(),
    WorkAction<ProcessDocsGradleAction.Parameters> {

    interface Parameters :
        ProcessDocsAction.Parameters,
        WorkParameters {
        override var baseDir: File
        override var sources: DokkaSourceSetImpl
        override var sourceRoots: List<File>
        override var target: File?
        override var exportAsHtmlDir: File?
        override var processors: List<String>
        override var processLimit: Int
        override var arguments: Map<String, Any?>
        override var outputReadOnly: Boolean
        override var htmlOutputReadOnly: Boolean
    }

    override val parameters: ProcessDocsAction.Parameters
        get() = getParameters()

    override fun execute() {
        try {
            process()
        } catch (e: Throwable) {
            e.printStackTrace(System.err)
            throw e
        }
    }
}
