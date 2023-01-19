package nl.jolanrensen.docProcessor

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project

class DocProcessorPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.repositories.mavenCentral()
    }
}



internal fun <T : Any> NamedDomainObjectContainer<T>.maybeCreate(name: String, configuration: T.() -> Unit): T {
    return findByName(name) ?: create(name, configuration)
}