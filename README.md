[![Maven metadata URL](https://img.shields.io/maven-metadata/v?label=Gradle%20Plugin&metadataUrl=https%3A%2F%2Fplugins.gradle.org%2Fm2%2Fnl%2Fjolanrensen%2FdocProcessor%2Fnl.jolanrensen.docProcessor.gradle.plugin%2Fmaven-metadata.xml)](https://plugins.gradle.org/plugin/nl.jolanrensen.docProcessor)

# KDoc / JavaDoc Preprocessor Gradle Plugin (Alpha)

This Gradle plugin allows you to preprocess your KDoc / JavaDoc comments with custom preprocessors and obtain modified
sources.

These preprocessors can be used to add custom tags to your KDoc / JavaDoc comments or change the entirety of the
comment.
This is not a Dokka plugin, meaning you can actually get a `sources.jar` file with the modified comments instead of just
having the comments modified in a `javadoc.jar` or a Dokka HTML website.

Note: `{@inline tags}` work in KDoc comments too! Plus, `{@tags {@inside tags}}` are supported too.

## Example

### What you write:

<pre><code><style>
.kdoc { color: #629755; font-style: italic }
.linkInKDoc { color: #8A653B }
.preprocessor { color: #93A629; font-weight: bold }
.comment { color: #808080 }
.annotation { color: #BBB529 }
.keyword { color: #CC7832; font-weight: bold }
.funDeclaration { color: #FFC66D; font-weight: bold }
.todo { color:#A8C023; }
</style>
<span class="kdoc">
/**
 * ## Submit Number
 * Submits the given number.
 *
 * ### For example
 * <span class="preprocessor">{@comment Giving an example of how the function can be called, default the argument to `5.0`}</span>
 * ```kotlin
 * MyClass().submit(<span class="preprocessor">${<span class="linkInKDoc">[SubmitDocs.ExampleArg]</span>=</span>5.0<span class="preprocessor"
>}</span>, File("file.json")) { println(it) }
 * ```
 *
 * ### Result
 * The number will be submitted to a JSON file like this:
 * ```json
 * <span class="preprocessor">{@includeFile (./submitted.json)}</span>
 * ```
 * <span class="preprocessor">@get <span class="linkInKDoc">[ExtraInfoArg]</span> {@comment Attempt to retrieve the <span class="linkInKDoc">[ExtraInfoArg]</span> variable}</span>
 * <span class="preprocessor">$<span class="linkInKDoc">[ParamArg]</span> {@comment Attempt to retrieve the <span class="linkInKDoc">[ParamArg]</span> variable using shorter notation}</span>
 * <b>@param</b> <span class="linkInKDoc">location</span> The file location to submit the number to.
 * <b>@param</b> <span class="linkInKDoc">onException</span> What to do when an exception occurs.
 * <b>@return</b> `true` if the number was submitted successfully, `false` otherwise.
 */</span>
<span class="annotation">@ExcludeFromSources</span>
<span class="keyword">private interface</span> SubmitDocs <b>{</b>

&nbsp;&nbsp;&nbsp;&nbsp;<span class="comment">/* Example argument, defaults to 5.0 */</span>
&nbsp;&nbsp;&nbsp;&nbsp;<span class="keyword">interface</span> ExampleArg

&nbsp;&nbsp;&nbsp;&nbsp;<span class="comment">/* Optional extra info */</span>
&nbsp;&nbsp;&nbsp;&nbsp;<span class="keyword">interface</span> ExtraInfoArg

&nbsp;&nbsp;&nbsp;&nbsp;<span class="comment">/* The param part */</span>
&nbsp;&nbsp;&nbsp;&nbsp;<span class="keyword">interface</span> ParamArg
<b>}</b>

<span class="kdoc">
/**
 * <span class="preprocessor">@include <span class="linkInKDoc">[SubmitDocs]</span></span>
 * <span class="preprocessor">@set <span class="linkInKDoc">[SubmitDocs.ParamArg]</span></span> @param <span class="linkInKDoc">[number]</span> The <span class="linkInKDoc">[Int]</span> to submit.
 * <span class="preprocessor">@set <span class="linkInKDoc">[SubmitDocs.ExampleArg]</span></span> 5<span class="preprocessor">{@comment Overriding the default example argument}</span>
 * <span class="preprocessor">@comment While You can use block tags for multiline comments, most of the time, inline tags are clearer:</span>
 * <span class="preprocessor">{@set <span class="linkInKDoc">[SubmitDocs.ExtraInfoArg]</span></span>
 *  ### This function can also be used from Java:
 *  <span class="preprocessor">{@sample <span class="linkInKDoc">[Submitting.sample]</span>}</span>
 * <span class="preprocessor">}</span>
 */</span>
<span class="keyword">public fun</span> <span class="funDeclaration">submit</span>(number: Int<span class="keyword">,</span> location: File<span class="keyword">,</span> onException: (e: Exception) -> Unit): Boolean = <span class="todo">TODO()</span>

<span class="kdoc">/** <span class="preprocessor">@include <span class="linkInKDoc">[SubmitDocs]</span> {@set <span class="linkInKDoc">[SubmitDocs.ParamArg]</span> </span>@param <span class="linkInKDoc">[number]</span> The <span class="linkInKDoc">[Double]</span> to submit.<span class="preprocessor">}</span> */</span>
<span class="keyword">public fun</span> <span class="funDeclaration">submit</span>(number: Double<span class="keyword">,</span> location: File<span class="keyword">,</span> onException: (e: Exception) -> Unit): Boolean = <span class="todo">TODO()</span>
</code></pre>

### What you get:

<pre><code>
<style>
.kdoc { color: #629755; font-style: italic }
.linkInKDoc { color: #8A653B }
.preprocessor { color: #93A629; font-weight: bold }
.comment { color: #808080 }
.annotation { color: #BBB529 }
.keyword { color: #CC7832; font-weight: bold }
.funDeclaration { color: #FFC66D; font-weight: bold }
.todo { color:#A8C023; }
</style>
<span class="kdoc">
/**
 * ## Submit Number
 * Submits the given number.
 *
 * ### For example
 *
 * ```kotlin
 * MyClass().submit(5, File("file.json")) { println(it) }
 * ```
 *
 * ### Result
 * The number will be submitted to a JSON file like this:
 * ```json
 * {
 *   "number": 5.0
 * }
 * ```
 * ### This function can also be used from Java:
 *  ```java
 * int number = 1;
 * File file = new File("file.json");
 * boolean result = TestKt.submit(number, file, e -> {
 *     System.out.println(e.getMessage());
 *     return Unit.INSTANCE;
 * });
 * ```
 * <b>@param</b> <span class="linkInKDoc">[number]</span> The <span class="linkInKDoc">[Int]</span> to submit. 
 * <b>@param</b> <span class="linkInKDoc">location</span> The file location to submit the number to.
 * <b>@param</b> <span class="linkInKDoc">onException</span> What to do when an exception occurs.
 * <b>@return</b> `true` if the number was submitted successfully, `false` otherwise.
 */</span>
<span class="keyword">public fun</span> <span class="funDeclaration">submit</span>(number: Int<span class="keyword">,</span> location: File<span class="keyword">,</span> onException: (e: Exception) -> Unit): Boolean = <span class="todo">TODO()</span>

<span class="kdoc">
/** ## Submit Number
 * Submits the given number.
 *
 * ### For example
 *
 * ```kotlin
 * MyClass().submit(5.0, File("file.json")) { println(it) }
 * ```
 *
 * ### Result
 * The number will be submitted to a JSON file like this:
 * ```json
 * {
 *   "number": 5.0
 * }
 * ```
 *
 * <b>@param</b> <span class="linkInKDoc">[number]</span> The <span class="linkInKDoc">[Double]</span> to submit. 
 * <b>@param</b> <span class="linkInKDoc">location</span> The file location to submit the number to.
 * <b>@param</b> <span class="linkInKDoc">onException</span> What to do when an exception occurs.
 * <b>@return</b> `true` if the number was submitted successfully, `false` otherwise. */</span>
<span class="keyword">public fun</span> <span class="funDeclaration">submit</span>(number: Double<span class="keyword">,</span> location: File<span class="keyword">,</span> onException: (e: Exception) -> Unit): Boolean = <span class="todo">TODO()</span>
</code></pre>

## Preprocessors

Preprocessors are run one at a time, in order, on all KDoc / JavaDoc comments in the sources.
If a preprocessor is a tag processor, it will process only its own tags in the following order:

- Inline tags
    - depth-first
    - top-to-bottom
    - left-to-right
- Block tags
    - top-to-bottom

Included preprocessors are:

| Description                                                                                                                                                                                                                                                                                    | Name                            |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------|
| `@include` tag to include other documentation into your KDoc / JavaDoc.<br/>Used like `@include [Reference.To.Element]`.                                                                                                                                                                       | `INCLUDE_DOC_PROCESSOR`         |
| `@includeFile` tag to include the entire content of a file into your KDoc / JavaDoc.<br/>Used like `@includeFile (./path/to/file)`.                                                                                                                                                            | `INCLUDE_FILE_DOC_PROCESSOR`    |
| `@set` / `@get` (or `$`) tags to define and retrieve variables within a KDoc / JavaDoc. Powerful in combination with `@include`.<br/>Used like `@set KEY some content`, `@get KEY some default`.<br/>Shortcuts for `{@get .}` are `$KEY`, `$KEY=default`, `${KEY}`, and `${KEY=some default}`. | `ARG_DOC_PROCESSOR`             |
| `@comment` tag to comment out parts of your modified KDoc / JavaDoc.<br/>Used like `@comment Some comment text`.                                                                                                                                                                               | `COMMENT_DOC_PROCESSOR`         |
| `@sample` / `@sampleNoComments` tags to include code samples into your KDoc / JavaDoc.<br/>Used like `@sample [Reference.To.Element]`.<br/>If present, only code in between `// SampleStart` and `// SampleEnd` is taken. `@sampleNoComments` excludes KDoc / JavaDoc from the sample.         | `SAMPLE_DOC_PROCESSOR`          |
| A processor that removes all escape characters ("\\") from your KDoc / JavaDoc comments.                                                                                                                                                                                                       | `REMOVE_ESCAPE_CHARS_PROCESSOR` |
| A processor that removes all KDoc / JavaDoc comments.                                                                                                                                                                                                                                          | `NO_DOC_PROCESSOR`              |
| A processor that adds a `/** TODO */` comment wherever there is no KDoc / JavaDoc comment.                                                                                                                                                                                                     | `TODO_DOC_PROCESSOR`            |

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

In `build.gradle.kts` or `build.gradle` add `id("nl.jolanrensen.docProcessor") version "{ VERSION }"`
to `plugins { .. }`.

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

In `build.gradle.kts` or `build.gradle` add `id("nl.jolanrensen.docProcessor") version "{ VERSION }"`
to `plugins { .. }`.

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
        ARG_DOC_PROCESSOR, // The @set and @get / $ processor
        COMMENT_DOC_PROCESSOR, // The @comment processor
        SAMPLE_DOC_PROCESSOR, // The @sample and @sampleNoComments processor
        REMOVE_ESCAPE_CHARS_PROCESSOR, // The processor that removes escape characters

        "com.example.plugin.ExampleDocProcessor", // A custom processor if you have one, see below
    )

    // Optional. Send specific arguments to processors.
    arguments += ARG_DOC_PROCESSOR_LOG_NOT_FOUND to false

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
            ArgDocProcessorKt.ARG_DOC_PROCESSOR, // The @set and @get / $ processor
            CommentDocProcessorKt.COMMENT_DOC_PROCESSOR, // The @comment processor
            SampleDocProcessorKt.SAMPLE_DOC_PROCESSOR, // The @sample and @sampleNoComments processor
            RemoveEscapeCharsProcessorKt.REMOVE_ESCAPE_CHARS_PROCESSOR, // The processor that removes escape characters

            "com.example.plugin.ExampleDocProcessor", // A custom processor if you have one, see below
    )

    // Optional. Send specific arguments to processors.
    arguments[IncludeArgDocProcessorKt.ARG_DOC_PROCESSOR] = false

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
- `ARG_DOC_PROCESSOR`: The `@set` and `@get` / `$` processor. This runs `@set` first and then `@get` / `$`.
- `COMMENT_DOC_PROCESSOR`: The `@comment` processor
- `SAMPLE_DOC_PROCESSOR`: The `@sample` and `@sampleNoComments` processor
- `REMOVE_ESCAPE_CHARS_PROCESSOR`: The processor that removes escape characters

This order ensures that `@set`/`@get` are processed after `@include` and `@includeFile` such that any arguments
that appear by them are available for the `@set`/`@get` processor.
The `@comment` processor is recommended to be after `@set`/`@get` too, as it can be used as a line break for
tag blocks. `@sample` and `@sampleNoComments` are recommended to be last of the tag processors, as processing of inline
tags inside comments of `@sample` might not be desired. Finally, the `REMOVE_ESCAPE_CHARS_PROCESSOR` is recommended to
be last to clean up any escape characters that might have been introduced by the user to evade some parts of the docs
from being processed.

## Technicalities

KDocs and JavaDocs are structured in a tree-like structure and are thus also parsed and processed like that.
For example, the following KDoc:

```kotlin
/**
 * Some extra text
 * @a [Test2]
 * Hi there!
 * @b source someFun
 * Some more text. {@c
 * @d [Test1] (
 * }
 * @e)
 */
```

will be split up in blocks as follows:

```
[
  "\nSome extra text",
  "@a [Test2]\nHi there!",
  "@b source someFun\nSome more text. (\n{@c [Test1] (\n}",
  "@e)\n",
]
```

This is also how tag processors receive their block-data. Note that any newlines after the `@tag`
are also included as part of the tag data. Tag processors can then decide what to do with this extra data.
However, `@include`, `@sample`, and `@includeFile` all have systems in place that
will keep the content after the tag and on the lines below the tag in place.
Take this into account when writing your own processors.

To avoid any confusion, it's usually easier to stick to `{@inline tags}` as then it's clear which part of the doc
belongs to the tag and what does not. Inline tags are processed before block tags per processor.

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
    compileOnly("nl.jolanrensen.docProcessor:doc-processor-gradle-plugin:{ VERSION }")
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
        var newContent =
            "Hi from the example doc processor! Here's the content after the @example tag: \"$contentWithoutTag\""

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

## Preview: IntelliJ plugin

I'm working on an IntelliJ plugin that will allow you to preview the documentation rendered directly in the IDE.
Currently, the only way to try this is by building the plugin yourself from sources and installing it in IntelliJ.
The plugin in its current state is unconfigurable and just uses the default processors as shown in the sample above.
Also, it uses the IDE engine to resolve references.
This is because it's a lot faster than my own engine + Dokka, but it does mean that there might be some differences
with the preview and how it will look in the final docs. So, take this into account.

I'm still working on connecting it to the Gradle plugin somehow or provide a way to configure it correctly,
but until then, you can use it as is and be more efficient in your documentation writing!