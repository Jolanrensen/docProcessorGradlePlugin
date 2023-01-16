# KDoc @include Gradle Plugin

Adds the @include modifier to KDocs to reuse written docs. 
Anything you can target with `[target]` with KDoc can be included in the current KDoc and will replace the `@include` line with the other content one to one.
Visibility modifiers are ignored for now.
JavaDoc is also supported. Add the `"java"` extension to `fileExtensions` in the plugin setup to use it.
You can even cross include between Java and Kotlin but no conversion whatsoever will be done at the moment.

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
 * @see [com.example.plugin.KdocIncludePlugin] 
 */
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
 * @see [com.example.plugin.KdocIncludePlugin] 
 */
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

...

plugins {
    id("nl.jolanrensen.kdocInclude") version "1.0-SNAPSHOT"
    ...
}

repositories {
    mavenLocal()
    ...
}

...

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
    outputs.upToDateWhen { false }

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

...

// As a bonus, this will update dokka if you use that
tasks.withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
    dokkaSourceSets {
        all {
            sourceRoot(processKdocIncludeMain.target.get())
        }
    }
}
```
## How it works

 - The sources provided to the plugin are read and analysed by 
[Dokka's default SourceToDocumentableTranslators](https://kotlin.github.io/dokka/1.6.0/developer_guide/extension_points/#creating-documentation-models).
 - All [Documentable](https://kotlin.github.io/dokka/1.6.0/developer_guide/data_model/#documentable-model) 
elements are filtered to be "linkable" and have documentation, after which they are saved in a map by their 
path (e.g. `com.example.plugin.Class1.function1`).
 - Next, the documentation contents, location in the file, and indents are collected from each documentable 
in the map.
 - All documentables are then iterated over and the `@include` lines are replaced with the documentation 
of the target documentable if found.
 - Finally, all files from the source are copied over to a destination folder and if there are any modifications that
need to be made in a file, the specified ranges for each documentation are replaced with the new documentation.
