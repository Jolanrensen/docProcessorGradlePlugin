package nl.jolanrensen.docProcessor.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens

/**
 * Makes sure the completion popup appears when the
 * user types a '{' or '$' in a KDoc comment.
 */
class KDocCompletionInvoker : TypedHandlerDelegate() {

    private fun autoPopupIfInKdoc(project: Project, editor: Editor) {
        AutoPopupController.getInstance(project).autoPopupMemberLookup(editor) { file ->
            val offset = editor.caretModel.offset
            // val chars = editor.document.charsSequence
            val elementAtCaret = file.findElementAt(offset - 1)
            val lastNodeType = elementAtCaret?.node?.elementType ?: return@autoPopupMemberLookup false

            lastNodeType === KDocTokens.TEXT
        }
    }

    override fun beforeCharTyped(
        c: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
        fileType: FileType,
    ): Result {
        if (file.fileType != KotlinFileType.INSTANCE) return Result.CONTINUE
        when (c) {
            // @ is already handled by KotlinTypedHandler

            '{', '$' -> autoPopupIfInKdoc(project, editor)
        }
        return Result.CONTINUE
    }
}
