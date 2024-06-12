package nl.jolanrensen.docProcessor.gradle


import mu.KotlinLogging
import nl.jolanrensen.docProcessor.defaultProcessors.ARG_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.COMMENT_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.EXPORT_AS_HTML_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.INCLUDE_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.INCLUDE_FILE_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.SAMPLE_DOC_PROCESSOR
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

private val log = KotlinLogging.logger { }

/**
 * Process doc task you can instantiate in your build.gradle(.kts) file.
 * For example using [Project.creatingProcessDocTask].
 */
abstract class ProcessDocTask @Inject constructor(factory: ObjectFactory) : DefaultTask() {

    /** Source root folders for preprocessing. This needs to be set! */
    @get:InputFiles
    val sources: ListProperty<File> = factory
        .listProperty(File::class)

    /** Source root folders for preprocessing. This needs to be set! */
    fun sources(files: Iterable<File>): Unit = sources.set(files)

    /**
     * Set base directory which will be used for relative source paths.
     * By default, it is '$projectDir'.
     */
    @get:Input
    val baseDir: Property<File> = factory
        .property(File::class)
        .convention(project.projectDir)

    /**
     * Set base directory which will be used for relative source paths.
     * By default, it is '$projectDir'.
     */
    fun baseDir(file: File): Unit = baseDir.set(file)

    /**
     * Target folder to place the preprocessing results in.
     */
    @get:Input
    val target: Property<File> = factory
        .property(File::class)
        .convention(File(project.buildDir, "docProcessor${File.separatorChar}${taskIdentity.name}"))

    /**
     * Target folder to place the preprocessing results in.
     */
    fun target(file: File): Unit = target.set(file)

    /**
     * Target folder of @ExportAsHtml Docs
     *
     * Defaults to $target/htmlExports
     */
    @get:Input
    val exportAsHtmlDir: Property<File> = factory
        .property(File::class)
        .convention(File(target.get(), "htmlExports"))

    /**
     * Target folder of @ExportAsHtml Docs
     *
     * Defaults to $target/htmlExports
     */
    fun exportAsHtmlDir(file: File): Unit = exportAsHtmlDir.set(file)

    /**
     * Where the generated sources are placed.
     */
    @get:OutputFiles
    val targets: FileCollection = factory.fileCollection()

    /**
     * The limit for while-true loops in processors. This is to prevent infinite loops.
     */
    @get:Input
    val processLimit: Property<Int> = factory
        .property(Int::class)
        .convention(10_000)

    /**
     * The limit for while-true loops in processors. This is to prevent infinite loops.
     */
    fun processLimit(int: Int): Unit = processLimit.set(int)

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
    @get:Input
    val processors: ListProperty<String> = factory
        .listProperty(String::class)
        .convention(
            listOf(
                INCLUDE_DOC_PROCESSOR,
                INCLUDE_FILE_DOC_PROCESSOR,
                ARG_DOC_PROCESSOR,
                COMMENT_DOC_PROCESSOR,
                SAMPLE_DOC_PROCESSOR,
                EXPORT_AS_HTML_DOC_PROCESSOR,
                REMOVE_ESCAPE_CHARS_PROCESSOR,
            )
        )

    /**
     * The processors to use. These must be fully qualified names, such as:
     * `"com.example.plugin.MyProcessor"`
     */
    fun processors(vararg strings: String): Unit = processors.set(strings.toList())

    /**
     * The arguments to be passed on to the processors.
     */
    @get:Input
    val arguments: MapProperty<String, Any?> = factory
        .mapProperty(String::class, Any::class)
        .convention(emptyMap())

    /**
     * The arguments to be passed on to the processors.
     */
    fun arguments(map: Map<String, Any?>): Unit = arguments.set(map)

    /**
     * The arguments to be passed on to the processors.
     */
    fun arguments(vararg arguments: Pair<String, Any?>): Unit = this.arguments.set(arguments.toMap())

    /** The classpath of this task. */
    @Classpath
    val classpath: Configuration = project.maybeCreateRuntimeConfiguration()

    /** Used by the task to execute [ProcessDocsGradleAction]. */
    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    private fun Project.maybeCreateRuntimeConfiguration(): Configuration =
        project.configurations.maybeCreate("kotlinKdocIncludePluginRuntime") {
            isCanBeConsumed = true
            val kotlinVersion = "1.8.10"
            dependencies.add(project.dependencies.create("org.jetbrains.kotlin:kotlin-compiler:$kotlinVersion"))
            dependencies.add(project.dependencies.create("org.jetbrains.dokka:dokka-analysis:$kotlinVersion")) // compileOnly in base plugin
            dependencies.add(project.dependencies.create("org.jetbrains.dokka:dokka-base:$kotlinVersion"))
            dependencies.add(project.dependencies.create("org.jetbrains.dokka:dokka-core:$kotlinVersion"))
        }

    private fun <T : Any> NamedDomainObjectContainer<T>.maybeCreate(name: String, configuration: T.() -> Unit): T =
        findByName(name) ?: create(name, configuration)

    inner class DependencySetPluginDsl {

        /**
         * Adds a plugin dependency to the classpath of this task.
         * Don't forget to add any new processor to the [processors] list.
         *
         * @param dependencyNotation Dependency notation
         */
        fun plugin(dependencyNotation: Any) {
            dependencies.add(project.dependencies.create(dependencyNotation))
        }

        /**
         * Gets the set of declared dependencies directly contained in this configuration
         * (ignoring superconfigurations).
         * <p>
         * This method does not resolve the configuration. Therefore, the return value does not include
         * transitive dependencies.
         *
         * @return the set of dependencies
         * @see #extendsFrom(Configuration...)
         */
        val dependencies: DependencySet = classpath.dependencies
    }

    /**
     * DSL to add plugin dependencies to the current task. If you want to include a processor from an external library,
     * that library needs to be added to the classpath of this task using this DSL.
     *
     * For example:
     *
     * ```groovy
     * dependencies.plugin("com.example.plugin:my-doc-processor-plugin:1.4.32")
     * ```
     */
    @get:Internal
    val dependencies = DependencySetPluginDsl()

    /**
     * DSL to add plugin dependencies to the current task. If you want to include a processor from an external library,
     * that library needs to be added to the classpath of this task using this DSL.
     *
     * For example:
     *
     * ```groovy
     * dependencies {
     *     plugin "com.example.plugin:my-doc-processor-plugin:1.4.32"
     * }
     * ```
     */
    fun dependencies(action: Action<DependencySetPluginDsl>): Unit = action.execute(dependencies)

    init {
        outputs.upToDateWhen {
            target.get().let {
                it.exists() && it.listFiles()?.isNotEmpty() == true
            }
        }
    }

    @TaskAction
    fun process() {
        // redirect println to INFO logs
        logging.captureStandardOutput(LogLevel.INFO)

        // redirect System.err to ERROR logs
        logging.captureStandardError(LogLevel.ERROR)

        log.lifecycle { "Doc Processor is running!" }

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

        log.info { "Using target folder: $target" }
        log.info { "Using source folders: $sourceRoots" }
        log.info { "Using target folders: ${targets.files.toList()}" }
        log.info { "Using runtime classpath: ${runtime.joinToString("\n")}" }

//        val sourceSetName = "sourceSet"
//        val sources = GradleDokkaSourceSetBuilder(
//            name = sourceSetName,
//            project = project,
//            sourceSetIdFactory = { DokkaSourceSetID(it, sourceSetName) },
//        ).apply {
//            sourceRoots.forEach {
//                if (it.exists()) sourceRoot(it)
//            }
//        }.build()

        val workQueue = workerExecutor.classLoaderIsolation {
            it.classpath.setFrom(runtime)
        }

        workQueue.submit(ProcessDocsGradleAction::class.java) {
            it.baseDir = baseDir.get()
//            it.sources = sources
            it.sourceRoots = sourceRoots
            it.target = target
            it.processors = processors
            it.processLimit = processLimit.get()
            it.arguments = arguments.get()
            it.exportAsHtmlDir = exportAsHtmlDir.get()
        }
    }
}

