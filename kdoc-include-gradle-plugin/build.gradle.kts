import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`
    java
    kotlin("jvm") version "1.7.20"
    id("com.gradle.plugin-publish") version "1.0.0"
    idea
}

group = "nl.jolanrensen.kdocInclude"
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
    // Use JUnit test framework for unit tests
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:5.5.4")
    implementation(gradleApi())


    // this is causing issues
    compileOnly("org.jetbrains.dokka:dokka-analysis:1.7.20")

    // Use JUnit test framework for unit tests
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:5.5.4")
    implementation(gradleApi())
    implementation("org.jetbrains.dokka:dokka-base:1.7.20")
    implementation("org.jetbrains.dokka:dokka-core:1.7.20")
    implementation("org.jetbrains.dokka:dokka-base-test-utils:1.7.20")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.7.20")

    // get included with dokka-analysis
//    compileOnly("org.jetbrains.dokka:kotlin-analysis-compiler:1.7.20")
//    compileOnly("org.jetbrains.dokka:kotlin-analysis-intellij:1.7.20")

}

gradlePlugin {

    // Define the plugin
    val kdocInclude by plugins.creating {
        id = "nl.jolanrensen.kdocInclude"
        displayName = "KDoc @include Gradle Plugin"
        description = "KDoc @include Gradle Plugin"
        implementationClass = "nl.jolanrensen.kdocInclude.KdocIncludePlugin"
    }

    val kdocIncludeJitpack by plugins.creating {
        id = "com.github.Jolanrensen.kdocIncludeGradlePlugin"
        displayName = "KDoc @include Gradle Plugin"
        description = "KDoc @include Gradle Plugin"
        implementationClass = "nl.jolanrensen.kdocInclude.KdocIncludePlugin"
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


