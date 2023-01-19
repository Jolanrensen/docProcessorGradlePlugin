package nl.jolanrensen.docProcessor

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.file.FileCollection
import java.io.File

@JvmInline
value class ProcessDocTaskDsl private constructor(public val task: ProcessDocTask) {

    constructor(task: ProcessDocTask, sources: Iterable<File>) : this(task.also { it.sources.set(sources) })

    fun task(action: ProcessDocTask.() -> Unit): ProcessDocTask = task.apply(action)


    /**
     * Source root folders for preprocessing. Needs to be set!
     */
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
     * List of file extensions to be included into preprocessing.
     * By default: kt, kts
     */
    var fileExtensions: List<String>
        get() = task.fileExtensions.get()
        set(value) {
            task.fileExtensions.set(value)
        }

    /**
     * Target folder to place preprocessing result in regular source processing
     * phase.
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
     * Whether to print debug information.
     */
    var debug: Boolean
        get() = task.debug.get()
        set(value) {
            task.debug.set(value)
        }

    var processors: List<String>
        get() = task.processors.get()
        set(value) {
            task.processors.set(value)
        }

    val classpath: Configuration
        get() = task.classpath

    val dependencies: DependencySet
        get() = task.classpath.dependencies

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