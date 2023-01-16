import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.0"
    id("com.vanniktech.maven.publish") version "0.20.0"
}

group = "nl.jolanrensen.kdocInclude"
version = "1.0-SNAPSHOT"

repositories {
    // Use Maven Central for resolving dependencies
    mavenCentral()
    maven("https://plugins.gradle.org/m2/")
}

mavenPublishing {

}

val compileOnlyApi by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = true
}

dependencies {
    // Use JUnit test framework for unit tests
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:5.5.4")
    implementation(gradleApi())
    implementation("org.jetbrains.dokka:dokka-core:1.7.20")
    implementation("org.jetbrains.dokka:dokka-base:1.7.20")
    implementation("org.jetbrains.dokka:dokka-base-test-utils:1.7.20")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.7.20")

    // this is causing issues
    implementation("org.jetbrains.dokka:dokka-analysis:1.7.20")

    // get included with dokka-analysis
//    compileOnly("org.jetbrains.dokka:kotlin-analysis-compiler:1.7.20")
//    compileOnly("org.jetbrains.dokka:kotlin-analysis-intellij:1.7.20")

}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}


