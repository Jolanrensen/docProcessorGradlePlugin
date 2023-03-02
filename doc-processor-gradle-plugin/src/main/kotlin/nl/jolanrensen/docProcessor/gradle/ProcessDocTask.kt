package nl.jolanrensen.docProcessor.gradle

import nl.jolanrensen.docProcessor.SimpleLogger
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.gradle.GradleDokkaSourceSetBuilder
import java.io.File
import javax.inject.Inject

/**
 * Process doc task you can instantiate in your build.gradle(.kts) file.
 * For example using [Project.creatingProcessDocTask].
 */
abstract class ProcessDocTask @Inject constructor(factory: ObjectFactory) : DefaultTask(), SimpleLogger {

    /** Source root folders for preprocessing. This needs to be set! */
    @get:InputFiles
    val sources: ListProperty<File> = factory
        .listProperty(File::class.java)

    /**
     * Set base directory which will be used for relative source paths.
     * By default, it is '$projectDir'.
     */
    @get:Input
    val baseDir: Property<File> = factory
        .property(File::class.java)
        .convention(project.projectDir)

    /**
     * Target folder to place the preprocessing results in.
     */
    @get:Input
    val target: Property<File> = factory
        .property(File::class.java)
        .convention(File(project.buildDir, "docProcessor${File.separatorChar}${taskIdentity.name}"))

    /**
     * Where the generated sources are placed.
     */
    @get:OutputFiles
    val targets: FileCollection = factory.fileCollection()

    /**
     * Whether to print debug information.
     */
    @get:Input
    val debug: Property<Boolean> = factory
        .property(Boolean::class.java)
        .convention(false)

    /**
     * The limit for while-true loops in processors. This is to prevent infinite loops.
     */
    @get:Input
    val processLimit: Property<Int> = factory
        .property(Int::class.java)
        .convention(10_000)

    /**
     * The processors to use. These must be fully qualified names, such as:
     * `"com.example.plugin.MyProcessor"`
     */
    @get:Input
    val processors: ListProperty<String> = factory
        .listProperty(String::class.java)
        .convention(emptyList())

    /** The classpath of this task. */
    @Classpath
    val classpath: Configuration = project.maybeCreateRuntimeConfiguration()

    /** Used by the task to execute [ProcessDocsGradleAction]. */
    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    private fun Project.maybeCreateRuntimeConfiguration(): Configuration =
        project.configurations.maybeCreate("kotlinKdocIncludePluginRuntime") {
            isCanBeConsumed = true
            dependencies.add(project.dependencies.create("org.jetbrains.kotlin:kotlin-compiler:1.7.20"))
            dependencies.add(project.dependencies.create("org.jetbrains.dokka:dokka-analysis:1.7.20")) // compileOnly in base plugin
            dependencies.add(project.dependencies.create("org.jetbrains.dokka:dokka-base:1.7.20"))
            dependencies.add(project.dependencies.create("org.jetbrains.dokka:dokka-core:1.7.20"))
        }

    private fun <T : Any> NamedDomainObjectContainer<T>.maybeCreate(name: String, configuration: T.() -> Unit): T =
        findByName(name) ?: create(name, configuration)

    /**
     * Adds dependency to plugin.
     * Don't forget to add any new processor to the [processors] list.
     *
     * @param dependencyNotation Dependency notation
     *
     * @see org.gradle.api.artifacts.dsl.DependencyHandler.create
     */
    fun addPlugins(vararg dependencyNotation: Any) {
        classpath.dependencies.addAll(
            dependencyNotation.map { project.dependencies.create(it) }
        )
    }

    /**
     * Adds dependency to plugin.
     * Don't forget to add any new processor to the [processors] list.
     *
     * @param dependency Dependency
     */
    fun addPlugins(vararg dependency: Dependency) {
        classpath.dependencies.addAll(dependency)
    }

    override val logEnabled: Boolean
        get() = debug.get()

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun process() {
        println("Hello from plugin 'nl.jolanrensen.docProcessor'")

        val sourceRoots = sources.get()
        val target = target.get()
        val runtime = classpath.resolve()
        val processors = processors.get()

        val relativeSources = sourceRoots.map { it.relativeTo(baseDir.get()) }
        (targets as ConfigurableFileCollection).setFrom(
            relativeSources.map {
                File(target, it.path)
            }
        )

        if (target.exists()) target.deleteRecursively()
        target.mkdir()

        println("Using target folder: $target")
        println("Using source folders: $sourceRoots")
        println("Using target folders: ${targets.files.toList()}")
        println("Using runtime classpath: ${runtime.joinToString("\n")}")

        val sourceSetName = "sourceSet"
        val sources = GradleDokkaSourceSetBuilder(
            name = sourceSetName,
            project = project,
            sourceSetIdFactory = { DokkaSourceSetID(it, sourceSetName) },
        ).apply {
            sourceRoots.forEach {
                if (it.exists()) sourceRoot(it)
            }
        }.build()

        val workQueue = workerExecutor.classLoaderIsolation {
            it.classpath.setFrom(runtime)
        }

        workQueue.submit(ProcessDocsGradleAction::class.java) {
            it.baseDir = baseDir.get()
            it.sources = sources
            it.sourceRoots = sourceRoots
            it.target = target
            it.debug = debug.get()
            it.processors = processors
            it.processLimit = processLimit.get()
        }
    }
}

