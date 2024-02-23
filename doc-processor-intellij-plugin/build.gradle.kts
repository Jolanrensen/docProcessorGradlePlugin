import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    kotlin("jvm")
    id("org.jetbrains.intellij") version "1.16.1"
    id("com.github.johnrengelman.shadow")
}

group = "nl.jolanrensen.docProcessor"
version = "0.3.4-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://www.jetbrains.com/intellij-repository/snapshots") {
        mavenContent { snapshotsOnly() }
    }
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide")
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://www.myget.org/F/rd-snapshots/maven/")
}

intellij {
    version.set("2023.3")
    type.set("IC")
    pluginName.set("DocProcessor")
    plugins.addAll(
        "org.jetbrains.kotlin",
        "com.intellij.java",
    )
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":doc-processor-common"))

    // Use JUnit test framework for unit tests
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:5.5.5")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}