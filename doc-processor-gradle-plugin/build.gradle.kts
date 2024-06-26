import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`
    java
    kotlin("jvm")
    id("com.gradle.plugin-publish") version "1.1.0"
    signing
    id("com.github.johnrengelman.shadow")
}

group = "nl.jolanrensen.docProcessor"
version = "0.3.11-SNAPSHOT"

publishing {
    repositories {
        maven {
            name = "localPluginRepository"
            url = uri("~/.m2/repository")
        }
    }
}

repositories {
    // Use Maven Central for resolving dependencies
    mavenCentral()
    maven("https://plugins.gradle.org/m2/")
}

dependencies {
    api(project(":doc-processor-common"))

    // Gradle plugin dependencies
    shadow(gradleApi())
    shadow(gradleKotlinDsl())

    // Dokka dependencies
    val dokkaVersion = "1.8.10"
    shadow("org.jetbrains.dokka:dokka-analysis:$dokkaVersion")
    shadow("org.jetbrains.dokka:dokka-base:$dokkaVersion")
    shadow("org.jetbrains.dokka:dokka-core:$dokkaVersion")
    shadow("org.jetbrains.dokka:dokka-base-test-utils:$dokkaVersion")
    shadow("org.jetbrains.dokka:dokka-gradle-plugin:$dokkaVersion")

    // logging
    api("io.github.microutils:kotlin-logging:3.0.5")

    // Use JUnit test framework for unit tests
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:5.5.5")
}

tasks.withType(ShadowJar::class) {
    isZip64 = true
    archiveClassifier = ""

    // Avoid clashes with org.jetbrains:markdown-jvm:0.6.1 in :common
    relocate("org.intellij.markdown", "nl.jolanrensen.docProcessor.markdown")
}

gradlePlugin {
    website = "https://github.com/Jolanrensen/docProcessorGradlePlugin"
    vcsUrl = "https://github.com/Jolanrensen/docProcessorGradlePlugin"
    // Define the plugin
    val docProcessor by plugins.creating {
        id = "nl.jolanrensen.docProcessor"
        displayName = "KDoc/Javadoc processor Gradle Plugin"
        description = "KDoc/Javadoc processor Gradle Plugin"
        tags = listOf(
            "kotlin",
            "java",
            "documentation",
            "library",
            "preprocessor",
            "plugins",
            "documentation-tool",
            "javadoc",
            "documentation-generator",
            "library-management",
            "kdoc",
            "javadocs",
            "preprocessors",
            "kdocs",
            "tags",
            "tag",
            "tag-processor",
        )
        implementationClass = "nl.jolanrensen.docProcessor.gradle.DocProcessorPlugin"
    }
}

// Add a source set and a task for a functional test suite
val functionalTest: SourceSet by sourceSets.creating
gradlePlugin.testSourceSets(functionalTest)

configurations[functionalTest.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())

val functionalTestTask = tasks.register<Test>("functionalTest") {
    testClassesDirs = functionalTest.output.classesDirs
    classpath = configurations[functionalTest.runtimeClasspathConfigurationName] +
            functionalTest.output
}

tasks.check {
    // Run the functional tests as part of `check`
    dependsOn(functionalTestTask)
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}
