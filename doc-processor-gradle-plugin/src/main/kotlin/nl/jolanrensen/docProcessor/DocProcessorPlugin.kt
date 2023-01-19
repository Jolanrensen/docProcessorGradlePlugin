package nl.jolanrensen.docProcessor

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.creating
import java.io.File

class DocProcessorPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        repositories.mavenCentral()
    }
}

fun TaskContainer.creatingProcessDocTask(sources: Iterable<File>, block: ProcessDocTaskDsl.() -> Unit) =
    creating(ProcessDocTask::class) {
        ProcessDocTaskDsl(this, sources).block()
    }

fun TaskContainer.createProcessDocTask(name: String, sources: Iterable<File>, block: ProcessDocTaskDsl.() -> Unit): ProcessDocTask =
    create<ProcessDocTask>(name) {
        ProcessDocTaskDsl(this, sources).block()
    }

fun TaskContainer.maybeCreateProcessDocTask(name: String, sources: Iterable<File>, block: ProcessDocTaskDsl.() -> Unit): ProcessDocTask =
    maybeCreate(name, ProcessDocTask::class.java).apply {
        ProcessDocTaskDsl(this, sources).block()
    }

fun Project.maybeCreateProcessDocTask(name: String, sources: Iterable<File>, block: ProcessDocTaskDsl.() -> Unit): ProcessDocTask =
    tasks.maybeCreateProcessDocTask(name, sources, block)

fun Project.createProcessDocTask(name: String, sources: Iterable<File>, block: ProcessDocTaskDsl.() -> Unit): ProcessDocTask =
    tasks.createProcessDocTask(name, sources, block)

fun Project.creatingProcessDocTask(sources: Iterable<File>, block: ProcessDocTaskDsl.() -> Unit) =
    tasks.creatingProcessDocTask(sources, block)

internal fun <T : Any> NamedDomainObjectContainer<T>.maybeCreate(name: String, configuration: T.() -> Unit): T {
    return findByName(name) ?: create(name, configuration)
}