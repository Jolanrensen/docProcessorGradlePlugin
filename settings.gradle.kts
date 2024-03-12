rootProject.name = "doc-processor-gradle-plugin"

include("doc-processor-common")
include("doc-processor-gradle-plugin")
include("doc-processor-intellij-plugin")

pluginManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        gradlePluginPortal()
        mavenLocal()
    }
}
