package nl.jolanrensen.kdocInclude

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.attributes.Usage
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


abstract class ProcessKdocIncludeTask @Inject constructor(factory: ObjectFactory) : DefaultTask() {

    /**
     * Source root folders for preprocessing
     */
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
     * List of file extensions to be included into preprocessing.
     * By default: kt, kts
     */
    @get:Input
    val fileExtensions: ListProperty<String> = factory
        .listProperty(String::class.java)
        .convention(listOf("kt", "kts"))

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    /**
     * Target folder to place preprocessing result in regular source processing
     * phase.
     */
    @get:Input
    val target: Property<File> = factory
        .property(File::class.java)
        .convention(File(project.buildDir, "kdocInclude${File.separatorChar}${taskIdentity.name}"))

    @get:OutputFiles
    val targets: FileCollection = factory.fileCollection()

    @get:Input
    val debug: Property<Boolean> = factory
        .property(Boolean::class.java)
        .convention(false)


    // Dokka uses these two, but they don't seem to do much in this project.

    @Classpath
    val pluginsClasspath: Configuration = project.maybeCreateDokkaPluginConfiguration()

    @Classpath
    val classpath: Configuration = project.maybeCreateRuntimeConfiguration()

    private fun Project.maybeCreateRuntimeConfiguration(): Configuration =
        project.configurations.maybeCreate("kotlinKdocIncludePluginRuntime") {
            isCanBeConsumed = true
            dependencies.add(project.dependencies.create("org.jetbrains.kotlin:kotlin-compiler:1.7.20"))
            dependencies.add(project.dependencies.create("org.jetbrains.dokka:dokka-analysis:1.7.20")) // compileOnly in base plugin
            dependencies.add(project.dependencies.create("org.jetbrains.dokka:dokka-base:1.7.20"))
            dependencies.add(project.dependencies.create("org.jetbrains.dokka:dokka-core:1.7.20"))
        }

    private fun Project.maybeCreateDokkaPluginConfiguration(
        additionalDependencies: Collection<Dependency> = emptySet(),
    ): Configuration = project.configurations.maybeCreate("kotlinKdocIncludePlugin") {
        attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, "java-runtime"))
        isCanBeConsumed = false
        dependencies.add(project.dependencies.create("org.jetbrains.dokka:dokka-analysis:1.7.20")) // compileOnly in base plugin
        dependencies.add(project.dependencies.create("org.jetbrains.dokka:dokka-base:1.7.20"))
        dependencies.addAll(additionalDependencies)
    }


    private fun println(message: String) {
        if (debug.get()) kotlin.io.println(message)
    }

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun process() {
        println("Hello from plugin 'nl.jolanrensen.kdocInclude'")

        val fileExtensions = fileExtensions.get()
        val sourceRoots = sources.get()
        val target = target.get()
        val pluginsClasspath = pluginsClasspath.resolve()
        val runtime = classpath.resolve()

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
        println("Using file extensions: $fileExtensions")
        println("Using plugin classpath: ${pluginsClasspath.joinToString("\n")}")
        println("Using runtime classpath: ${runtime.joinToString("\n")}")

        val workQueue = workerExecutor.classLoaderIsolation {
            it.classpath.setFrom(runtime)
        }

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

        workQueue.submit(ProcessKdocIncludeAction::class.java) {
            it.pluginsClasspath = pluginsClasspath
            it.baseDir = baseDir.get()
            it.sources = sources
            it.sourceRoots = sourceRoots
            it.target = target
        }
    }
}

