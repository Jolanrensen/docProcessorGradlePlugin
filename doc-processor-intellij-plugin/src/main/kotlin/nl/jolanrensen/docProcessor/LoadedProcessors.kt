package nl.jolanrensen.docProcessor

import nl.jolanrensen.docProcessor.defaultProcessors.ARG_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.ARG_DOC_PROCESSOR_LOG_NOT_FOUND
import nl.jolanrensen.docProcessor.defaultProcessors.ArgDocProcessor
import nl.jolanrensen.docProcessor.defaultProcessors.COMMENT_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.CommentDocProcessor
import nl.jolanrensen.docProcessor.defaultProcessors.EXPORT_AS_HTML_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.INCLUDE_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.INCLUDE_DOC_PROCESSOR_PRE_SORT
import nl.jolanrensen.docProcessor.defaultProcessors.INCLUDE_FILE_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.IncludeDocProcessor
import nl.jolanrensen.docProcessor.defaultProcessors.IncludeFileDocProcessor
import nl.jolanrensen.docProcessor.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.SAMPLE_DOC_PROCESSOR
import nl.jolanrensen.docProcessor.defaultProcessors.SampleDocProcessor

/**
 * Loads all processors that are included in the plugin with the correct settings.
 *
 * TODO make customizable
 */
fun Any.getLoadedProcessors(): List<DocProcessor> {
    Thread.currentThread().contextClassLoader = this.javaClass.classLoader

    return findProcessors(
        fullyQualifiedNames = listOf(
            INCLUDE_DOC_PROCESSOR,
            INCLUDE_FILE_DOC_PROCESSOR,
            ARG_DOC_PROCESSOR,
            COMMENT_DOC_PROCESSOR,
            SAMPLE_DOC_PROCESSOR,
            EXPORT_AS_HTML_DOC_PROCESSOR,
            REMOVE_ESCAPE_CHARS_PROCESSOR,
        ),
        arguments = mapOf(
            ARG_DOC_PROCESSOR_LOG_NOT_FOUND to false,
            INCLUDE_DOC_PROCESSOR_PRE_SORT to false,
        ),
    )
}

/**
 * TODO make customizable
 */
fun getTagsInUse(): List<String> =
    listOf(
        IncludeDocProcessor.TAG,
        IncludeFileDocProcessor.TAG,
        CommentDocProcessor.TAG,
    ) + ArgDocProcessor.RETRIEVE_ARGUMENT_TAGS +
        ArgDocProcessor.DECLARE_ARGUMENT_TAGS +
        SampleDocProcessor.TAGS
