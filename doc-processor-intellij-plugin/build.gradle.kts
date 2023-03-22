import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    kotlin("jvm")
    id("org.jetbrains.intellij") version "1.13.2"
}

group = "nl.jolanrensen.docProcessor"
version = "0.2.0-SNAPSHOT"

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
    version.set("2022.1.1")
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.6.4")

//    val dokkaVersion = "1.8.10"
//    implementation("org.jetbrains.dokka:dokka-analysis:$dokkaVersion")

//    implementation("org.jetbrains.kotlin:kotlin-compiler:1.8.10")
//    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.8.10")

//    val kotlinPluginVersion = "222-1.8.10-release-430-IJ4167.29"
//    implementation("org.jetbrains.kotlin:common:$kotlinPluginVersion")
//    implementation("org.jetbrains.kotlin:idea:$kotlinPluginVersion")
//    implementation("org.jetbrains.kotlin:core:$kotlinPluginVersion")
//    implementation("org.jetbrains.kotlin:native:$kotlinPluginVersion")

//    val ideaVersion = "213.6777.52"
//    implementation("com.jetbrains.intellij.idea:intellij-core:$ideaVersion")
//    implementation("com.jetbrains.intellij.idea:jps-standalone:$ideaVersion")

    // Use JUnit test framework for unit tests
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:5.5.5")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}