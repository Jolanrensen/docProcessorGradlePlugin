import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.0.0"
    id("com.github.johnrengelman.shadow")
    id("org.jlleitschuh.gradle.ktlint")
}

group = "nl.jolanrensen.docProcessor"
version = "0.4.0-beta1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
    mavenLocal()
    maven("https://www.jetbrains.com/intellij-repository/snapshots") {
        mavenContent { snapshotsOnly() }
    }
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide")
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://www.myget.org/F/rd-snapshots/maven/")
}

intellijPlatform {
    pluginConfiguration {
        name = "DocProcessor"
        ideaVersion {
            sinceBuild = "231"
            untilBuild = "243.*"
        }
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    api(project(":doc-processor-common"))

    // Use JUnit test framework for unit tests
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:5.5.5")

    intellijPlatform {
        intellijIdeaCommunity("2024.2")
        bundledPlugins(
            "org.jetbrains.kotlin",
            "com.intellij.java",
        )
        zipSigner()
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
