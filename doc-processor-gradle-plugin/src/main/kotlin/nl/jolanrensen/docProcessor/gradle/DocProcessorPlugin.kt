@file:Suppress("unused", "RedundantVisibilityModifier")

package nl.jolanrensen.docProcessor.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.creating
import java.io.File

/**
 * Gradle plugin part of the doc-processor project.
 *
 * Extension functions in this file enable users to more easily create a [ProcessDocTask] in their build.gradle.kts
 * file.
 */
class DocProcessorPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit =
        with(project) {
            // add maven central to repositories, which is needed to add dokka as a dependency in ProcessDocTasks
            repositories.mavenCentral()
        }
}

/**
 * Create a new [ProcessDocTask] using by-delegate.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * val processDocs by tasks.creatingProcessDocTask(sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
public fun TaskContainer.creatingProcessDocTask(sources: Iterable<File>, block: ProcessDocTaskDsl.() -> Unit) =
    creating(ProcessDocTask::class) {
        ProcessDocTaskDsl(this, sources).block()
    }

/**
 * Create a new [ProcessDocTask].
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * tasks.creatingProcessDocTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
public fun TaskContainer.createProcessDocTask(
    name: String,
    sources: Iterable<File>,
    block: ProcessDocTaskDsl.() -> Unit,
) = create<ProcessDocTask>(name) {
    ProcessDocTaskDsl(this, sources).block()
}

/**
 * Create a new [ProcessDocTask] if one with this name doesn't already exist.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * tasks.maybeCreateProcessDocTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
public fun TaskContainer.maybeCreateProcessDocTask(
    name: String,
    sources: Iterable<File>,
    block: ProcessDocTaskDsl.() -> Unit,
) = maybeCreate(name, ProcessDocTask::class.java).apply {
    ProcessDocTaskDsl(this, sources).block()
}

/**
 * Create a new [ProcessDocTask] if one with this name doesn't already exist.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * maybeCreateProcessDocTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 */
public fun Project.maybeCreateProcessDocTask(
    name: String,
    sources: Iterable<File>,
    block: ProcessDocTaskDsl.() -> Unit,
) = tasks.maybeCreateProcessDocTask(name, sources, block)

/**
 * Create a new [ProcessDocTask].
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * creatingProcessDocTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
public fun Project.createProcessDocTask(name: String, sources: Iterable<File>, block: ProcessDocTaskDsl.() -> Unit) =
    tasks.createProcessDocTask(name, sources, block)

/**
 * Create a new [ProcessDocTask] using by-delegate.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * val processDocs by creatingProcessDocTask(sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
public fun Project.creatingProcessDocTask(sources: Iterable<File>, block: ProcessDocTaskDsl.() -> Unit) =
    tasks.creatingProcessDocTask(sources, block)
