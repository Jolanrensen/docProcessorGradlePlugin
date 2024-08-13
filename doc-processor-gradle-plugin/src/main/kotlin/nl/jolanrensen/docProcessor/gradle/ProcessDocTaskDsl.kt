@file:Suppress("RedundantVisibilityModifier", "unused")

package nl.jolanrensen.docProcessor.gradle

import nl.jolanrensen.docProcessor.defaultProcessors.ARG_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.COMMENT_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.INCLUDE_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.INCLUDE_FILE_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.SAMPLE_DOC_PROCESSOR
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.file.FileCollection
import java.io.File

/**
 * Kotlin DSL-like wrapper around [ProcessDocTask].
 */
class ProcessDocTaskDsl private constructor(public val task: ProcessDocTask) {

    constructor(task: ProcessDocTask, sources: Iterable<File>) : this(task.also { it.sources.set(sources) })

    /** Access the DSL of the original [task][ProcessDocTask]. */
    fun task(action: ProcessDocTask.() -> Unit): ProcessDocTask = task.apply(action)

    /** Source root folders for preprocessing. This needs to be set! */
    var sources: List<File>
        get() = task.sources.get()
        set(value) {
            task.sources.set(value)
        }

    /**
     * Set base directory which will be used for relative source paths.
     * By default, it is '$projectDir'.
     */
    var baseDir: File
        get() = task.baseDir.get()
        set(value) {
            task.baseDir.set(value)
        }

    /**
     * Target folder to place the preprocessing results in.
     */
    var target: File
        get() = task.target.get()
        set(value) {
            task.target.set(value)
        }

    /**
     * Whether the output at [target] should be read-only.
     * Defaults to `true`.
     */
    var outputReadOnly: Boolean
        get() = task.outputReadOnly.get()
        set(value) {
            task.outputReadOnly.set(value)
        }

    inner class ExportAsHtmlDsl internal constructor() {
        operator fun invoke(action: ExportAsHtmlDsl.() -> Unit): Unit = action()

        /**
         * Target folder of @ExportAsHtml Docs
         *
         * Defaults to $target/htmlExports
         */
        var dir: File
            get() = task.exportAsHtmlDir.get()
            set(value) {
                task.exportAsHtmlDir.set(value)
            }

        /**
         * Whether the output at [dir] should be read-only.
         * Defaults to `true`.
         */
        var outputReadOnly: Boolean
            get() = task.htmlOutputReadOnly.get()
            set(value) {
                task.htmlOutputReadOnly.set(value)
            }

        // TODO more settings
    }

    /**
     * DSL for configuring the @ExportAsHtml task.
     */
    val exportAsHtml = ExportAsHtmlDsl()

    /**
     * Where the generated sources are placed.
     */
    val targets: FileCollection
        get() = task.targets

    /**
     * The limit for while-true loops in processors. This is to prevent infinite loops.
     */
    var processLimit: Int
        get() = task.processLimit.get()
        set(value) {
            task.processLimit.set(value)
        }

    /**
     * The processors to use. These must be fully qualified names, such as:
     * `"com.example.plugin.MyProcessor"`
     *
     * Defaults to:
     * [[INCLUDE_DOC_PROCESSOR],
     *  [INCLUDE_FILE_DOC_PROCESSOR],
     *  [ARG_DOC_PROCESSOR],
     *  [COMMENT_DOC_PROCESSOR],
     *  [SAMPLE_DOC_PROCESSOR],
     *  [REMOVE_ESCAPE_CHARS_PROCESSOR]]
     */
    var processors: List<String>
        get() = task.processors.get()
        set(value) {
            task.processors.set(value)
        }

    /**
     * The arguments to be passed on to the processors.
     */
    var arguments: Map<String, Any?>
        get() = task.arguments.get()
        set(value) {
            task.arguments.set(value)
        }

    /** The classpath of this task. */
    val classpath: Configuration
        get() = task.classpath

    /** The dependencies of this task. */
    val dependencies: DependencySet
        get() = task.classpath.dependencies

    /**
     * DSL to add plugin dependencies to the current task. If you want to include a processor from an external library,
     * that library needs to be added to the classpath of this task using this DSL.
     *
     * For example:
     *
     * ```kotlin
     * dependencies {
     *     plugin("com.example.plugin:my-doc-processor-plugin:1.4.32")
     * }
     * ```
     */
    fun dependencies(action: DependencySet.() -> Unit): DependencySet = task.classpath.dependencies.apply(action)

    /**
     * Adds a plugin dependency to the classpath of this task.
     * Don't forget to add any new processor to the [processors] list.
     *
     * @receiver [MutableSet]
     * @param dependencyNotation Dependency notation
     */
    fun DependencySet.plugin(dependencyNotation: Any) {
        add(task.project.dependencies.create(dependencyNotation))
    }
}
