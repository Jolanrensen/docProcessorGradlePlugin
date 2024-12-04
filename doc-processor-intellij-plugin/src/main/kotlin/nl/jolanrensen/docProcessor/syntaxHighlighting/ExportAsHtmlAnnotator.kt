package nl.jolanrensen.docProcessor.syntaxHighlighting

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.OpenFileAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import nl.jolanrensen.docProcessor.DocumentableWrapper
import nl.jolanrensen.docProcessor.ExportAsHtml
import nl.jolanrensen.docProcessor.Mode
import nl.jolanrensen.docProcessor.annotationNames
import nl.jolanrensen.docProcessor.docProcessorIsEnabled
import nl.jolanrensen.docProcessor.mode
import nl.jolanrensen.docProcessor.renderToHtml
import nl.jolanrensen.docProcessor.services.DocProcessorServiceK2
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.resolve.calls.util.getValueArgumentsInParentheses
import java.io.File
import javax.swing.Icon

/**
 * Provides `< >` icons in the gutter for KDocs with the `@ExportAsHtml` tag.
 * Clicking the icon will open the exported HTML file in the editor.
 */
class ExportAsHtmlAnnotator : Annotator {

    class GutterIcon(val htmlFile: File) : GutterIconRenderer() {
        override fun getTooltipText(): String = "Click to preview exported HTML"

        override fun getClickAction(): AnAction {
            val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(htmlFile)
            return object : AnAction() {
                override fun actionPerformed(e: AnActionEvent) {
                    OpenFileAction.openFile(
                        file = virtualFile ?: return,
                        project = e.project ?: return,
                    )
                }
            }
        }

        override fun isNavigateAction(): Boolean = true

        override fun getIcon(): Icon = AllIcons.FileTypes.Html

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as GutterIcon

            return htmlFile.absolutePath == other.htmlFile.absolutePath
        }

        override fun hashCode(): Int = htmlFile.absolutePath.hashCode()

        override fun toString(): String = "GutterIcon(htmlFile=${htmlFile.absolutePath})"
    }

    private fun annotate(declaration: KtDeclaration, annotation: KtAnnotationEntry, holder: AnnotationHolder) {
        val service = DocProcessorServiceK2.getInstance(annotation.project)
        val documentableWrapper = service.getDocumentableWrapperOrNull(declaration) ?: return
        val processedDocumentableWrapper = service.getProcessedDocumentableWrapperOrNull(documentableWrapper) ?: return
        val arguments = annotation.getValueArgumentsInParentheses()
        val htmlFile = exportToHtmlFile(arguments, processedDocumentableWrapper)

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .needsUpdateOnTyping()
            .gutterIconRenderer(GutterIcon(htmlFile))
            .range(annotation.textRange)
            .create()
    }

    private val isEnabled get() = docProcessorIsEnabled && mode == Mode.K2

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!isEnabled) return
        if (element !is KtDeclaration) return
        if (element.docComment == null) return

        val hasExportAsHtmlTag = element.annotationNames.any {
            ExportAsHtml::class.simpleName!! in it
        }
        if (!hasExportAsHtmlTag) return

        val annotation = element.annotationEntries.firstOrNull {
            ExportAsHtml::class.simpleName!! in it.shortName!!.asString()
        } ?: return

        annotate(element, annotation, holder)
    }
}

private fun exportToHtmlFile(annotationArguments: List<ValueArgument>, doc: DocumentableWrapper): File {
    val themeArg = annotationArguments.firstOrNull {
        it.getArgumentName()?.asName?.toString() == ExportAsHtml::theme.name
    } ?: annotationArguments.getOrNull(0)?.takeIf {
        !it.isNamed() && it.getArgumentExpression()?.text?.toBoolean() != null
    }
    val theme = themeArg?.getArgumentExpression()?.text?.toBoolean() ?: true

    val stripReferencesArg = annotationArguments.firstOrNull {
        it.getArgumentName()?.asName?.toString() == ExportAsHtml::stripReferences.name
    } ?: annotationArguments.getOrNull(1)?.takeIf {
        !it.isNamed() && it.getArgumentExpression()?.text?.toBoolean() != null
    }
    val stripReferences = stripReferencesArg?.getArgumentExpression()?.text?.toBoolean() ?: true

    val html = doc
        .getDocContentForHtmlRange()
        .renderToHtml(theme = theme, stripReferences = stripReferences)
    val file = File.createTempFile(doc.fullyQualifiedPath, ".html")
    file.writeText(html)

    return file
}
