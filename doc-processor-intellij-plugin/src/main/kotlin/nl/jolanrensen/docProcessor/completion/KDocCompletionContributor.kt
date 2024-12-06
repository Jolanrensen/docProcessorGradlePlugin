@file:Suppress("ktlint:standard:comment-wrapping")

package nl.jolanrensen.docProcessor.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns
import com.intellij.util.ProcessingContext
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.completion.Mode.AFTER_ASTERISK
import nl.jolanrensen.docProcessor.completion.Mode.AT_TAG_NAME
import nl.jolanrensen.docProcessor.completion.Mode.IN_TEXT
import nl.jolanrensen.docProcessor.defaultProcessors.ArgDocProcessor
import nl.jolanrensen.docProcessor.defaultProcessors.ExportAsHtmlDocProcessor
import nl.jolanrensen.docProcessor.defaultProcessors.IncludeDocProcessor
import nl.jolanrensen.docProcessor.getLoadedTagProcessors
import org.jetbrains.kotlin.idea.completion.or
import org.jetbrains.kotlin.idea.completion.singleCharPattern
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

/**
 * Contributes completions for new special KDoc tags.
 */
class KDocCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            // for block tags: activates after `*` or `/**`
            psiElement().afterLeaf(
                psiElement(KDocTokens.LEADING_ASTERISK) or psiElement(KDocTokens.START),
            ),
            KDocProcessorTagCompletionProvider(AFTER_ASTERISK),
        )
        extend(
            CompletionType.BASIC,
            // for block tags: activates at exactly `@xxx`, also in the middle of the tag name
            psiElement(KDocTokens.TAG_NAME),
            KDocProcessorTagCompletionProvider(AT_TAG_NAME),
        )
        extend(
            CompletionType.BASIC,
            // for inline tags: activates anywhere in the kdoc text
            psiElement(KDocTokens.TEXT),
            KDocProcessorTagCompletionProvider(IN_TEXT),
        )
    }
}

enum class Mode {
    AFTER_ASTERISK, // after `*` or `/**`
    AT_TAG_NAME, // at exactly `@xxx`, also in the middle of the tag name
    IN_TEXT, // anywhere in the kdoc text
}

class KDocProcessorTagCompletionProvider(private val mode: Mode) : CompletionProvider<CompletionParameters>() {
    private val activeTagDocProcessors = getLoadedTagProcessors()

    private fun LookupElementBuilder.withTailText(processor: TagDocProcessor): LookupElementBuilder =
        withTailText("  ${processor.name}")

    private fun LookupElementBuilder.withIcon(): LookupElementBuilder = withIcon(AllIcons.Gutter.JavadocRead)

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val charPattern = when (mode) {
            IN_TEXT -> singleCharPattern('@') or singleCharPattern('{') or singleCharPattern('$')
            AT_TAG_NAME, AFTER_ASTERISK -> singleCharPattern('@')
        }
        // findIdentifierPrefix() requires identifier part characters to be a superset of identifier start characters
        val prefix = CompletionUtil.findIdentifierPrefix(
            parameters.position.containingFile,
            parameters.offset,
            StandardPatterns.character().javaIdentifierPart() or charPattern,
            StandardPatterns.character().javaIdentifierStart() or charPattern,
        )

        if (parameters.isAutoPopup && prefix.isEmpty()) return

        // check special conditions for adding completions
        when (mode) {
            AFTER_ASTERISK -> {
                if (prefix.isNotEmpty() && !prefix.startsWith('@')) return
                val text = parameters.position.text.substringBefore('@')
                if (text.isNotBlank() || text.length > 3) return
            }

            AT_TAG_NAME -> if (prefix.isNotEmpty() && !prefix.startsWith('@')) return

            IN_TEXT -> if (prefix.isNotEmpty() && prefix.first() !in setOf('@', '{', '$')) return
        }

        val resultWithPrefix = result.withPrefixMatcher(prefix)
        for (processor in activeTagDocProcessors) {
            when (processor) {
                // special cases
                is IncludeDocProcessor,
                is ArgDocProcessor, // can enable or disable, depending on whether we want to include [] by default
                ->
                    for (tag in processor.providesTags) {
                        when (mode) {
                            IN_TEXT -> resultWithPrefix.addElement(
                                LookupElementBuilder.create("{@$tag []}")
                                    .withPresentableText("{@$tag }")
                                    .withInsertHandler { c, _ ->
                                        EditorModificationUtil.moveCaretRelatively(c.editor, -2)
                                    }
                                    .withIcon()
                                    .withTailText(processor),
                            )

                            AT_TAG_NAME, AFTER_ASTERISK -> resultWithPrefix.addElement(
                                LookupElementBuilder.create("@$tag []")
                                    .withPresentableText("@$tag")
                                    .withInsertHandler { c, _ ->
                                        EditorModificationUtil.moveCaretRelatively(c.editor, -1)
                                    }
                                    .withIcon()
                                    .withTailText(processor),
                            )
                        }
                    }

                is ExportAsHtmlDocProcessor ->
                    for (tag in processor.providesTags) {
                        when (mode) {
                            IN_TEXT -> resultWithPrefix.addElement(
                                LookupElementBuilder.create("{@$tag}")
                                    .withIcon()
                                    .withTailText(processor),
                            )

                            AT_TAG_NAME, AFTER_ASTERISK -> resultWithPrefix.addElement(
                                LookupElementBuilder.create("@$tag")
                                    .withIcon()
                                    .withTailText(processor),
                            )
                        }
                    }

                // default case
                else ->
                    for (tag in processor.providesTags) {
                        when (mode) {
                            IN_TEXT -> resultWithPrefix.addElement(
                                LookupElementBuilder.create("{@$tag }")
                                    .withInsertHandler { c, _ ->
                                        EditorModificationUtil.moveCaretRelatively(c.editor, -1)
                                    }
                                    .withIcon()
                                    .withTailText(processor),
                            )

                            AT_TAG_NAME, AFTER_ASTERISK -> resultWithPrefix.addElement(
                                LookupElementBuilder.create("@$tag ")
                                    .withIcon()
                                    .withTailText(processor),
                            )
                        }
                    }
            }
        }

        if (mode != IN_TEXT) return
        val argDocProcessor = activeTagDocProcessors.firstIsInstanceOrNull<ArgDocProcessor>() ?: return
        resultWithPrefix.addElement(
            LookupElementBuilder.create("$")
                .withIcon()
                .withTailText(argDocProcessor),
        )

        resultWithPrefix.addElement(
            LookupElementBuilder.create("\${}")
                .withInsertHandler { c, _ ->
                    EditorModificationUtil.moveCaretRelatively(c.editor, -1)
                }
                .withIcon()
                .withTailText(argDocProcessor),
        )
    }
}
