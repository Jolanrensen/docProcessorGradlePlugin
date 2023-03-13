import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`
    java
    kotlin("jvm") version "1.8.10"
    id("com.gradle.plugin-publish") version "1.0.0"
    idea
}

group = "nl.jolanrensen.docProcessor"
version = "1.0-SNAPSHOT"

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
    // Gradle plugin dependencies
    implementation(gradleApi())
    implementation(gradleKotlinDsl())

    // Dokka dependencies
    val dokkaVersion = "1.8.10"
    compileOnly("org.jetbrains.dokka:dokka-analysis:$dokkaVersion")
    api("org.jetbrains.dokka:dokka-base:$dokkaVersion")
    api("org.jetbrains.dokka:dokka-core:$dokkaVersion")
    api("org.jetbrains.dokka:dokka-base-test-utils:$dokkaVersion")
    api("org.jetbrains.dokka:dokka-gradle-plugin:$dokkaVersion")

    // logging
    api("io.github.microutils:kotlin-logging:1.5.9")

    // Use JUnit test framework for unit tests
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:5.5.4")
}

gradlePlugin {

    // Define the plugin
    val docProcessor by plugins.creating {
        id = "nl.jolanrensen.docProcessor"
        displayName = "KDoc/Javadoc processor Gradle Plugin"
        description = "KDoc/Javadoc processor Gradle Plugin"
        implementationClass = "nl.jolanrensen.docProcessor.gradle.DocProcessorPlugin"
    }

    // Define the plugin for JitPack
    val docProcessorJitpack by plugins.creating {
        id = "com.github.jolanrensen.docProcessorGradlePlugin"
        displayName = "KDoc/Javadoc processor Gradle Plugin"
        description = "KDoc/Javadoc processor Gradle Plugin"
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
    kotlinOptions.jvmTarget = "1.8"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
