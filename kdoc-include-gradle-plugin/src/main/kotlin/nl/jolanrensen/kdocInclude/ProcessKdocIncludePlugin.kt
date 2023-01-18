package nl.jolanrensen.kdocInclude

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project

class KdocIncludePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.repositories.mavenCentral()
    }
}



internal fun <T : Any> NamedDomainObjectContainer<T>.maybeCreate(name: String, configuration: T.() -> Unit): T {
    return findByName(name) ?: create(name, configuration)
}