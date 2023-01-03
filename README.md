# KDoc @include Gradle Plugin

Adds the @include modifier to KDocs to reuse written docs.

For example:
```kotlin
package com.example.plugin

/**
 * Hello World!
 * 
 * This is a large example of how the plugin will work
 * 
 * @param name The name of the person to greet
 * @see [com.example.plugin.KdocIncludePlugin]
 */
private interface Test1

/**
 * Hello World 2!
 * @include [Test1]
 */
@AnnotationTest(a = 24)
private interface Test2

/** 
 * Some extra text
 * @include [Test2] */
fun someFun() {
    println("Hello World!")
}

/** @include [com.example.plugin.Test2] */
fun someMoreFun() {
    println("Hello World!")
}
```
turns into:
```kotlin
package com.example.plugin

/**
 * Hello World!
 * 
 * This is a large example of how the plugin will work
 * 
 * @param name The name of the person to greet
 * @see [com.example.plugin.KdocIncludePlugin]
 */
private interface Test1

/**
 * Hello World 2!
 * 
 * Hello World!
 * 
 * This is a large example of how the plugin will work
 * 
 * @param name The name of the person to greet
 * @see [com.example.plugin.KdocIncludePlugin]
 */
@AnnotationTest(a = 24)
private interface Test2

/**
 * Some extra text
 * 
 * Hello World 2!
 * 
 * Hello World!
 * 
 * This is a large example of how the plugin will work
 * 
 * @param name The name of the person to greet
 * @see [com.example.plugin.KdocIncludePlugin] */
fun someFun() {
    println("Hello World!")
}

/**
 * Hello World 2!
 * 
 * Hello World!
 * 
 * This is a large example of how the plugin will work
 * 
 * @param name The name of the person to greet
 * @see [com.example.plugin.KdocIncludePlugin] */
fun someMoreFun() {
    println("Hello World!")
}
```

## How to use

Clone the project and run `./gradlew publishToMavenLocal` in the source folder.

In your project's `build.gradle.kts` add `mavenLocal()` to `repositories {}` and add `id("nl.jolanrensen.kdocInclude") version "1.0-SNAPSHOT"` to `plugins {}`.

Say you want to create a task that will run when you're making a sources Jar such that the modified files appear in the Jar:

```kts
import nl.jolanrensen.kdocInclude.ProcessKdocIncludeTask
import org.gradle.jvm.tasks.Jar

// Backup the kotlin source files location
val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories

// Create the processing task
val processKdocIncludeMain by tasks.creating(ProcessKdocIncludeTask::class) {
    // Point it to the right souces. This can also be the test sources for instance
    sources.set(kotlinMainSources)
    
    // Optional. Filter file extensions, by default [kt, kts]
    fileExtensions.set(listOf(...))
    
    // Optional. The target folder of the processed files. By default ${project.buildDir}/kdocInclude/${taskName}
    target.set(...)
}

// Modify all Jar tasks such that before running the Kotlin sources are set to 
// the target of processKdocIncludeMain and they are returned back to normal afterwards.
tasks.withType<Jar> {
    dependsOn(processKdocIncludeMain)

    doFirst {
        kotlin {
            sourceSets {
                main {
                    kotlin.setSrcDirs(
                        processKdocIncludeMain.targets
                    )
                }
            }
        }
    }

    doLast {
        kotlin {
            sourceSets {
                main {
                    kotlin.setSrcDirs(kotlinMainSources)
                }
            }
        }
    }
}

// As a bonus, this will update dokka if you use that
tasks.withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
    dokkaSourceSets {
        all {
            sourceRoot(processKdocIncludeMain.target.get())
        }
    }
}
```
