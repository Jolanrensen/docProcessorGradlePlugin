# KDoc / JavaDoc Preprocessor Gradle Plugin (PREVIEW)

This Gradle plugin allows you to preprocess your KDoc / JavaDoc comments with custom preprocessors.
These preprocessors can be used to add custom tags to your KDoc / JavaDoc comments or change the entirety of the comment.
Examples include: 
 - `@include` tag to include other comments into your KDoc / JavaDoc, see [@include Processor](#include-processor)
 - `@sample` / `@sampleNoComments` tag to include code samples into your KDoc / JavaDoc
 - A processor that removes all KDoc / JavaDoc comments
 - A processor that adds a `/** TODO */` comment wherever there is no KDoc / JavaDoc comment
 - A processor that makes all KDoc / JavaDoc comments uppercase (try and make this for fun!)
 - The sky is the limit :)

## How to get it

### From sources

Clone the project and run `./gradlew publishToMavenLocal` in the source folder.

In your project's `settings.gradle.kts` add 
```kts
pluginManagement { 
    repositories { 
        mavenLocal()
    } 
}
```

In `build.gradle.kts` add `id("nl.jolanrensen.docProcessor") version "1.0-SNAPSHOT"` to `plugins {}`.

### From JitPack

In your project's `settings.gradle.kts` add 
```kts
pluginManagement { 
    repositories { 
        maven(url = "https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            requested.apply {
                // careful with other com.github.* plugins, change to "com.github.jolanrensen.docProcessorGradlePlugin" if needed
                if ("$id".startsWith("com.github.")) {
                    val (_, _, user, name) = "$id".split(".", limit = 4)
                    useModule("com.github.$user:$name:$version")
                }
            }
        }
    }
}
```

In `build.gradle.kts` add `id("com.github.jolanrensen.docProcessorGradlePlugin") version "main-SNAPSHOT"` to `plugins {}`.

## How to use

Say you want to create a task that will run when you're making a sources Jar such that the modified files appear in the Jar:

```kts
import nl.jolanrensen.docProcessor.*
import nl.jolanrensen.docProcessor.defaultProcessors.*
import org.gradle.jvm.tasks.Jar

..

plugins {
    // When taking the plugin from sources
    id("nl.jolanrensen.docProcessor") version "1.0-SNAPSHOT"
    
    // When taking the plugin from JitPack
    id("com.github.jolanrensen.docProcessorGradlePlugin") version "main-SNAPSHOT"
    ..
}

repositories {
    mavenLocal()
    ..
}

..

// Backup the kotlin source files location
val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories

// Create the processing task and point it to the right sources. 
// This can also be the test sources for instance.
val processKdocMain by creatingProcessDocTask(sources = kotlinMainSources) {
    
    // Optional. The target folder of the processed files. By default ${project.buildDir}/docProcessor/${taskName}.
    target = File(..)
    
    // Optional. If you want to see more logging. By default false.
    debug = true
    
    // The processors you want to use in this task.
    processors = listOf(
        INCLUDE_DOC_PROCESSOR, // The @include processor
        "com.example.plugin.ExampleDocProcessor", // A custom processor, see below
    )

    // Optional dependencies for this task. These dependencies can introduce custom processors.
    dependencies {
        plugin("com.example:plugin:SOME_VERSION")
    }
}

// Modify all Jar tasks such that before running the Kotlin sources are set to 
// the target of processKdocMain and they are returned back to normal afterwards.
tasks.withType<Jar> {
    dependsOn(processKdocMain)
    outputs.upToDateWhen { false }

    doFirst {
        kotlin {
            sourceSets {
                main {
                    kotlin.setSrcDirs(processKdocMain.targets)
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

..

// As a bonus, this will update dokka if you use that
tasks.withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
    dokkaSourceSets {
        all {
            for (root in processKdocMain.targets)
                sourceRoot(root)
        }
    }
}
```

## @include Processor

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

## How it works

 - The sources provided to the plugin are read and analysed by 
[Dokka's default SourceToDocumentableTranslators](https://kotlin.github.io/dokka/1.6.0/developer_guide/extension_points/#creating-documentation-models).
 - All [Documentables](https://kotlin.github.io/dokka/1.6.0/developer_guide/data_model/#documentable-model) are 
saved in a map by their path (e.g. `com.example.plugin.Class1.function1`).
 - Next, the documentation contents, location in the file, and indents are collected from each documentable 
in the map.
 - All processors are then run in sequence on the collected documentables with their data.
 - All documentables are then iterated over and tag replacement processors, like @include, will replace the tag with new content.
 - Finally, all files from the source are copied over to a destination folder and if there are any modifications that
need to be made in a file, the specified ranges for each documentation are replaced with the new documentation.

## Custom processors
You can create your plugin for the Gradle plugin with your own processors by either extending the abstract `TagDocProcessor` class or
implementing the `DocProcessor` interface, depending on how much control you need over the docs.

Let's create a small example processor:
```kotlin
package com.example.plugin

class ExampleDocProcessor : TagDocProcessor() {

    /** We'll intercept @example tags. */
    override fun tagIsSupported(tag: String): Boolean =
        tag == "example"

    override fun processTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWithSource,
        docContent: String,
        filteredDocumentables: Map<String, List<DocumentableWithSource>>,
        allDocumentables: Map<String, List<DocumentableWithSource>>,
    ): String {
        // We can get the content after the @example tag.
        val contentWithoutTag = tagWithContent
            .removePrefix("@example")
            .removeSurrounding("\n")
            .trim()
        
        // While we can play with the other arguments, let's just return some simple modified content
        
        return "Hi from the example doc processor! Here's the content after the @example tag: \"$contentWithoutTag\""
    }
}
```

For the processor to be detectable we need to add it to the 
`src/main/resources/META-INF/services/nl.jolanrensen.docProcessor.DocProcessor` file:
```
com.example.plugin.ExampleDocProcessor
```
and then publish the project somewhere it can be used in other projects.

Add the published project as dependency in your other project's `build.gradle.kts` file in your created
doc process task (as described in the [How to Use](#how-to-use) section), both in the dependencies
and in the `processors` list.

Now, if that project contains a function like:
```kotlin
/**
 * Main function.
 * @example Example
 */
fun main() {
    println("Hello World!")
}
```

The output will be:
```kotlin

/**
 * Main function.
 * Hi from the example doc processor! Here's the content after the @example tag: "Example"
 */
fun main() {
    println("Hello World!")
}
```

See the `defaultProcessor` folder in the project for more examples!
