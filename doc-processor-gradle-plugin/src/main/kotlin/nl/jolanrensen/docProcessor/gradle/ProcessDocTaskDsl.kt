@file:Suppress("RedundantVisibilityModifier", "unused")

package nl.jolanrensen.docProcessor.gradle

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.file.FileCollection
import java.io.File

/**
 * Kotlin DSL-like wrapper around [ProcessDocTask].
 */
@JvmInline
value class ProcessDocTaskDsl private constructor(public val task: ProcessDocTask) {

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
     */
    var processors: List<String>
        get() = task.processors.get()
        set(value) {
            task.processors.set(value)
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
    fun dependencies(action: DependencySet.() -> Unit): DependencySet =
        task.classpath.dependencies.apply(action)

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