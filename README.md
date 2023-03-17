[![Maven metadata URL](https://img.shields.io/maven-metadata/v?label=Gradle%20Plugin&metadataUrl=https%3A%2F%2Fplugins.gradle.org%2Fm2%2Fnl%2Fjolanrensen%2FdocProcessor%2Fnl.jolanrensen.docProcessor.gradle.plugin%2Fmaven-metadata.xml)](https://plugins.gradle.org/plugin/nl.jolanrensen.docProcessor)

# KDoc / JavaDoc Preprocessor Gradle Plugin (Alpha)

This Gradle plugin allows you to preprocess your KDoc / JavaDoc comments with custom preprocessors.
These preprocessors can be used to add custom tags to your KDoc / JavaDoc comments or change the entirety of the
comment.
This is not a Dokka plugin, meaning you can actually get a `sources.jar` file with the modified comments instead of just
having the comments modified in a `javadoc.jar` or a Dokka HTML website.

Note: `{@inline tags}` work in KDoc comments too! Plus, `{@tags {@inside tags}}` work too.

The processing order is:

- Inline tags
    - depth-first
    - top-to-bottom
    - left-to-right
- Block tags
    - top-to-bottom

Included preprocessors are:

| Description                                                                                                                      | Name                         |
|----------------------------------------------------------------------------------------------------------------------------------|------------------------------|
| `@include` tag to include other comments into your KDoc / JavaDoc, see [@include Processor](#include-processor).                 | `INCLUDE_DOC_PROCESSOR`      |
| `@includeFile` tag to include file content into your KDoc / JavaDoc                                                              | `INCLUDE_FILE_DOC_PROCESSOR` |
| `@arg` / `@includeArg` tags to define and include arguments within your KDoc / JavaDoc. Powerful in combination  with `@include` | `INCLUDE_ARG_DOC_PROCESSOR`  |
| `@comment` tag to comment out parts of your modified KDoc / JavaDoc                                                              | `COMMENT_DOC_PROCESSOR`      |
| `@sample` / `@sampleNoComments` tags to include code samples into your KDoc / JavaDoc                                            | `SAMPLE_DOC_PROCESSOR`       |
| A processor that removes all KDoc / JavaDoc comments                                                                             | `NO_DOC_PROCESSOR`           |
| A processor that adds a `/** TODO */` comment wherever there is no KDoc / JavaDoc comment                                        | `TODO_DOC_PROCESSOR`         |

Of course, you can also try to make your own preprocessor (see [Custom Processors](#custom-processors)).
For instance, you could make a processor that makes all KDoc / JavaDoc comments uppercase,
a tag processor that automatically inserts URLs to your website, or simply a processor that produces
errors or warnings for incorrect doc usage.

The sky is the limit :).

## How to get it

### From Gradle Plugins

In your project's `settings.gradle.kts` or `build.gradle` add:

```kts
pluginManagement {
    repositories {
        ..
        gradlePluginPortal()
    }
}
```

In `build.gradle.kts` or `build.gradle` add `id("nl.jolanrensen.docProcessor") version "{ VERSION }"` to `plugins { .. }`.

### From sources

Clone the project and run `./gradlew publishToMavenLocal` in the source folder.

In your project's `settings.gradle.kts` or `settings.gradle` add:

```kts
pluginManagement {
    repositories {
        ..
        mavenLocal()
    }
}
```

In `build.gradle.kts` or `build.gradle` add `id("nl.jolanrensen.docProcessor") version "{ VERSION }"` to `plugins { .. }`.

## How to use

Say you want to create a task that will run when you're making a sources Jar such that the modified files appear in the
Jar:

`build.gradle.kts`:

```kts
import nl.jolanrensen.docProcessor.gradle.*
import nl.jolanrensen.docProcessor.defaultProcessors.*
import org.gradle.jvm.tasks.Jar

..

plugins {
    id("nl.jolanrensen.docProcessor") version "{ VERSION }"
    ..
}

..

// Backup the kotlin source files location
val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories

// Create the processing task and point it to the right sources. 
// This can also be the test sources for instance.
val processKdocMain by creatingProcessDocTask(sources = kotlinMainSources) {

    // Optional. The target folder of the processed files. By default ${project.buildDir}/docProcessor/${taskName}.
    target = file(..)

    // The processors you want to use in this task.
    // The recommended order of default processors is as follows:
    processors = listOf(
        INCLUDE_DOC_PROCESSOR, // The @include processor
        INCLUDE_FILE_DOC_PROCESSOR, // The @includeFile processor
        INCLUDE_ARG_DOC_PROCESSOR, // The @arg and @includeArg processor
        COMMENT_DOC_PROCESSOR, // The @comment processor
        SAMPLE_DOC_PROCESSOR, // The @sample and @sampleNoComments processor
      
        "com.example.plugin.ExampleDocProcessor", // A custom processor if you have one, see below
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

// As a bonus, this will update dokka to use the processed files as sources as well.
tasks.withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
    dokkaSourceSets {
        all {
            sourceRoot(processKdocMain.target.get())
        }
    }
}
```

`build.gradle`:

```groovy
import nl.jolanrensen.docProcessor.gradle.*
import nl.jolanrensen.docProcessor.defaultProcessors.*
import org.gradle.jvm.tasks.Jar

..

plugins {
    id "nl.jolanrensen.docProcessor" version "{ VERSION }"
    ..
}

..

// Backup the kotlin source files location
def kotlinMainSources = kotlin.sourceSets.main.kotlin.sourceDirectories

// Create the processing task and point it to the right sources. 
// This can also be the test sources for instance.
def processKdocMain = tasks.register('processKdocMain', ProcessDocTask) {

    // Optional. The target folder of the processed files. By default ${project.buildDir}/docProcessor/${taskName}.
    target file(..)

    // The processors you want to use in this task.
    // The recommended order of default processors is as follows:
    processors(
            IncludeDocProcessorKt.INCLUDE_DOC_PROCESSOR, // The @include processor
            IncludeFileDocProcessorKt.INCLUDE_FILE_DOC_PROCESSOR, // The @includeFile processor
            IncludeArgDocProcessorKt.INCLUDE_ARG_DOC_PROCESSOR, // The @arg and @includeArg processor
            CommentDocProcessorKt.COMMENT_DOC_PROCESSOR, // The @comment processor
            SampleDocProcessorKt.SAMPLE_DOC_PROCESSOR, // The @sample and @sampleNoComments processor
      
        "com.example.plugin.ExampleDocProcessor", // A custom processor if you have one, see below
    )

    // Optional dependencies for this task. These dependencies can introduce custom processors.
    dependencies {
        plugin "com.example:plugin:SOME_VERSION"
    }
}.get()

// Modify all Jar tasks such that before running the Kotlin sources are set to 
// the target of processKdocMain and they are returned back to normal afterwards.
tasks.withType(Jar).configureEach {
    dependsOn(processKdocMain)
    outputs.upToDateWhen { false }

    doFirst {
        kotlin {
            sourceSets {
                main {
                    kotlin.srcDirs = processKdocMain.targets
                }
            }
        }
    }

    doLast {
        kotlin {
            sourceSets {
                main {
                    kotlin.srcDirs = kotlinMainSources
                }
            }
        }
    }
}

..

// As a bonus, this will update dokka to use the processed files as sources as well.
tasks.withType(org.jetbrains.dokka.gradle.AbstractDokkaLeafTask).configureEach {
    dokkaSourceSets.with {
        configureEach {
            sourceRoot(processKdocMain.target.get())
        }
    }
}
```

### Recommended order of default processors

While you can use the processors in any order and leave out some or include others, the recommended order is as follows:

 - `INCLUDE_DOC_PROCESSOR`: The `@include` processor
 - `INCLUDE_FILE_DOC_PROCESSOR`: The `@includeFile` processor
 - `INCLUDE_ARG_DOC_PROCESSOR`: The `@arg` and `@includeArg` processor
 - `COMMENT_DOC_PROCESSOR`: The `@comment` processor
 - `SAMPLE_DOC_PROCESSOR`: The `@sample` and `@sampleNoComments` processor

This order ensures that `@arg`/`@includeArg` is processed after `@include` and `@includeFile` such that any arguments
that appear by them are available for the `@arg`/`@includeArg` processor.
The `@comment` processor is recommended to be after `@arg`/`@includeArg` too, as it can be used as a line break for
tag blocks. `@sample` and `@sampleNoComments` are recommended to be last as processing of inline tags inside comments of 
`@sample` might not be desired.

## @include Processor

Adds the @include modifier to KDocs to reuse written docs.
Anything you can target with `[target]` with KDoc can be included in the current KDoc and will replace the `@include`
line with the other content one to one.
Visibility modifiers are ignored for now. Import statements are taken into account, however!
JavaDoc is also supported. Add the `"java"` extension to `fileExtensions` in the plugin setup to use it.
You can even cross include between Java and Kotlin but no conversion whatsoever will be done at the moment.

Example in conjunction with the `@arg` / `@includeArg` processor:

```kotlin
package com.example.plugin

/**
 * Hello World!
 *
 * This is a large example of how the plugin will work from {@includeArg source}
 *
 * @param name The name of the person to greet
 * @see [com.example.plugin.KdocIncludePlugin]
 * {@arg source Test1}
 */
private interface Test1

/**
 * Hello World 2!
 * @include [Test1]
 * {@arg source Test2}
 */
@AnnotationTest(a = 24)
private interface Test2

/**
 * Some extra text
 * @include [Test2]
 * {@arg source someFun} */
fun someFun() {
    println("Hello World!")
}

/** {@include [com.example.plugin.Test2]}{@arg source someMoreFun} */
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
 * This is a large example of how the plugin will work from Test1
 *
 * @param name The name of the person to greet
 * @see [com.example.plugin.KdocIncludePlugin]
 *
 */
private interface Test1

/**
 * Hello World 2!
 * Hello World!
 *
 * This is a large example of how the plugin will work from Test2
 *
 * @param name The name of the person to greet
 * @see [com.example.plugin.KdocIncludePlugin][com.example.plugin.KdocIncludePlugin]
 *
 */
@AnnotationTest(a = 24)
private interface Test2

/**
 * Some extra text
 * Hello World 2!
 * Hello World!
 *
 * This is a large example of how the plugin will work from someFun
 *
 * @param name The name of the person to greet
 * @see [com.example.plugin.KdocIncludePlugin][com.example.plugin.KdocIncludePlugin]
 */
fun someFun() {
    println("Hello World!")
}

/** Hello World 2!
 * Hello World!
 *
 * This is a large example of how the plugin will work from someMoreFun
 *
 * @param name The name of the person to greet
 * @see [com.example.plugin.KdocIncludePlugin][com.example.plugin.KdocIncludePlugin]
 */
fun someMoreFun() {
    println("Hello World!")
}
```

## Technicalities

KDocs and JavaDocs are structured in a tree-like structure and are thus also parsed and processed like that.
For example, the following KDoc:

```kotlin
/**
 * Some extra text
 * @a [Test2]
 * Hi there!
 * @b source someFun
 * Some more text. (
 * @c [Test1] {
 * )
 */
```

will be parsed as follows:

```
["
Some extra text",
"@a [Test2]
Hi there!",
"@b source someFun
Some more text. (
@c [Test1] {
)
"]
```

This is also how tag processors receive their data. Note that any newlines after the `@tag`
are also included as part of the tag data. Tag processors can then decide what to do with this extra data.
However, `@include`, `@includeArg`, `@sample`, and `@includeFile` all have systems in place that
will keep the content after the tag and on the lines below the tag in place.
Take this into account when writing your own processors.

To avoid any confusion, it's usually easier to stick to `{@inline tags}` as then it's clear which part of the doc
belongs to the tag and what does not. Inline tags are processed before block tags.

Take extra care when using tags that can introduce new tags, such as `@include`, as this will cause the structure
of the doc to change mid-processing. Very powerful, but also potentially dangerous.
If something weird happens, try to disable some processors to understand what's happening.

## How it works

- The sources provided to the plugin are read and analysed by
  [Dokka's default SourceToDocumentableTranslators](https://kotlin.github.io/dokka/1.6.0/developer_guide/extension_points/#creating-documentation-models).
- All [Documentables](https://kotlin.github.io/dokka/1.6.0/developer_guide/data_model/#documentable-model) are
  saved in a map by their path (e.g. `com.example.plugin.Class1.function1`).
- Next, the documentation contents, location in the file, and indents are collected from each documentable
  in the map.
- All processors are then run in sequence on the collected documentables with their data.
- All documentables are then iterated over and tag replacement processors, like @include, will replace the tag with new
  content.
- Finally, all files from the source are copied over to a destination folder and if there are any modifications that
  need to be made in a file, the specified ranges for each documentation are replaced with the new documentation.

## Custom processors

You can create your plugin for the Gradle plugin with your own processors by either extending the
abstract `TagDocProcessor` class or
implementing the `DocProcessor` interface, depending on how much control you need over the docs.

Make sure to depend on the sources by adding the following to your `build.gradle.kts` or `build.gradle` file:
```kts
repositories {
    ..
    gradlePluginPortal()
}

dependencies {
    ..
    implementation("nl.jolanrensen.docProcessor:doc-processor-gradle-plugin:{ VERSION }")
}
```

Let's create a small example processor:

```kotlin
package com.example.plugin

class ExampleDocProcessor : TagDocProcessor() {

    /** We'll intercept @example tags. */
    override fun tagIsSupported(tag: String): Boolean =
        tag == "example"
    
    /** How `{@inner tags}` are processed. */
    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = processContent(tagWithContent)
    
    /** How `  @normal tags` are processed. */
    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = processContent(tagWithContent)
    
    // We can use the same function for both processInnerTagWithContent and processTagWithContent
    private fun processContent(tagWithContent: String): String {
      
        // We can log stuff if we want to using https://github.com/oshai/kotlin-logging
        logger.info { "Hi from the example logs!" }
      
        // We can get the content after the @example tag.
        val contentWithoutTag = tagWithContent
            .getTagArguments(tag = "example", numberOfArguments = 1)
            .single()
            .trimEnd() // remove trailing whitespaces/newlines
            .removeEscapeCharacters() // remove escape character "\" from the content
      
        // While we can play with the other arguments, let's just return some simple modified content
        var newContent = "Hi from the example doc processor! Here's the content after the @example tag: \"$contentWithoutTag\""
      
        // Since we trimmed all trailing newlines from the content, we'll add one back if they were there.
        if (tagWithContent.endsWith("\n"))
            newContent += "\n"
      
        return newContent
    }
}
```

For the processor to be detectable, we need to add it to the
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
