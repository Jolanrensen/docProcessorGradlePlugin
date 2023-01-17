package nl.jolanrensen.kdocInclude

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project

class KdocIncludePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.repositories.mavenCentral()
        project.repositories.mavenLocal()

        // Something like this?
//        val config = project.configurations.maybeCreate("kdocIncludeDependencies") {
//            isCanBeConsumed = false
//
//            val dokkaAnalysis = project.dependencies.create("org.jetbrains.dokka:dokka-analysis:1.7.20")
//            val dokkaBase = project.dependencies.create("org.jetbrains.dokka:dokka-base:1.7.20")
//
//            dependencies.add(dokkaAnalysis)
//            dependencies.add(dokkaBase)
//        }

        // Doesn't work
//        project.dependencies.add("implementation", "org.jetbrains.dokka:dokka-analysis:1.7.20")

        project.tasks.withType(ProcessKdocIncludeTask::class.java) {

        }
    }
}



internal fun <T : Any> NamedDomainObjectContainer<T>.maybeCreate(name: String, configuration: T.() -> Unit): T {
    return findByName(name) ?: create(name, configuration)
}