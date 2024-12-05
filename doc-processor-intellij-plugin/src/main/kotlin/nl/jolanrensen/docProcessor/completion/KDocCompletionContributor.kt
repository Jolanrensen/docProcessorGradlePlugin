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
import com.intellij.patterns.StandardPatterns.or
import com.intellij.util.ProcessingContext
import nl.jolanrensen.docProcessor.TagDocProcessor
import nl.jolanrensen.docProcessor.defaultProcessors.ArgDocProcessor
import nl.jolanrensen.docProcessor.getLoadedTagProcessors
import org.jetbrains.kotlin.idea.completion.or
import org.jetbrains.kotlin.idea.completion.singleCharPattern
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

@Suppress("ktlint:standard:comment-wrapping")
class KDocCompletionContributor : CompletionContributor() {
    init {
        extend(
            /* type = */ CompletionType.BASIC,
            // place =
            psiElement().afterLeaf(
                psiElement(KDocTokens.LEADING_ASTERISK) or psiElement(KDocTokens.START),
            ),
            /* provider = */ KDocProcessorTagCompletionProvider,
        )
        extend(
            /* type = */ CompletionType.BASIC,
            /* place = */ psiElement(KDocTokens.TAG_NAME),
            /* provider = */ KDocProcessorTagCompletionProvider,
        )
    }
}

object KDocProcessorTagCompletionProvider : CompletionProvider<CompletionParameters>() {

    private val activeTagDocProcessors = getLoadedTagProcessors()

    private val TagDocProcessor.tailText get() = "  $name"

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        // findIdentifierPrefix() requires identifier part characters to be a superset of identifier start characters
        val prefix = CompletionUtil.findIdentifierPrefix(
            parameters.position.containingFile,
            parameters.offset,
            singleCharPattern('@') or singleCharPattern('{') or singleCharPattern('$'),
            singleCharPattern('@') or singleCharPattern('{') or singleCharPattern('$'),
        )

        // TODO detection of { and $ not yet working
        if (parameters.isAutoPopup && prefix.isEmpty()) return
        if (prefix.isNotEmpty() && prefix.first() !in setOf('@', '{', '$')) {
            return
        }
        val resultWithPrefix = result.withPrefixMatcher(prefix)
        for (processor in activeTagDocProcessors) {
            for (tag in processor.providesTags) {
                resultWithPrefix.addElement(
                    LookupElementBuilder.create("@$tag ")
                        .withIcon(AllIcons.Gutter.JavadocRead)
                        .withTailText(processor.tailText),
                )

                resultWithPrefix.addElement(
                    LookupElementBuilder.create("{@$tag }")
                        .withInsertHandler { c, _ ->
                            EditorModificationUtil.moveCaretRelatively(c.editor, -1)
                        }
                        .withIcon(AllIcons.Gutter.JavadocRead)
                        .withTailText(processor.tailText),
                )
            }
        }

        val argDocProcessor = activeTagDocProcessors.firstIsInstanceOrNull<ArgDocProcessor>() ?: return
        resultWithPrefix.addElement(
            LookupElementBuilder.create("$")
                .withIcon(AllIcons.Gutter.JavadocRead)
                .withTailText(argDocProcessor.tailText),
        )

        resultWithPrefix.addElement(
            LookupElementBuilder.create("\${}")
                .withIcon(AllIcons.Gutter.JavadocRead)
                .withInsertHandler { c, _ ->
                    EditorModificationUtil.moveCaretRelatively(c.editor, -1)
                }
                .withTailText(argDocProcessor.tailText),
        )
    }
}
