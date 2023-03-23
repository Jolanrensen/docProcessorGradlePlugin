package nl.jolanrensen.docProcessor

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.utils.fqname.getKotlinFqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import java.io.File

fun DocumentableWrapper.Companion.createFromIntellijOrNull(
    documentable: PsiElement,
): DocumentableWrapper? {
    require(documentable is KtDeclaration || documentable is PsiDocCommentOwner) {
        "Documentable must be a KtDeclaration or PsiDocCommentOwner, but was ${documentable::class.simpleName}"
    }

    val path = documentable.getKotlinFqName()?.asString() ?: return null
    val extensionPath: String? = if (documentable.isExtensionDeclaration()) {
        // TODO
        println("TODO: extension declaration")
        null
    } else null

    val file = File(documentable.containingFile.originalFile.virtualFile.path)

    if (!file.exists()) {
        return null
    }

    val fileText: String = file.readText()

    val docComment = documentable.docComment

    val docFileTextRange = docComment?.textRange?.let { ogRange ->
        // docComment.textRange is the range of the comment in the file, but depending on the language,
        // it might not exactly match the range of the comment from /** to */. Let's correct that.
        val query = ogRange.substring(fileText)
        val startComment = query.indexOf("/**")
        val endComment = query.lastIndexOf("*/")

        require(startComment != -1) {
            """
                    |Could not find start of comment.
                    |Paths: ${listOfNotNull(path, extensionPath)}
                    |Comment Content: "${docComment.text.getDocContentOrNull()}"
                    |Query: "$query"""".trimMargin()
        }
        require(endComment != -1) {
            """
                    |Could not find end of comment.
                    |Paths: ${listOfNotNull(path, extensionPath)}
                    |Comment Content: "${docComment.text.getDocContentOrNull()}"
                    |Query: "$query"""".trimMargin()
        }

        TextRange(ogRange.startOffset + startComment, ogRange.startOffset + endComment + 2)
    } ?: try {
        // if there is no comment, we give the text range for where a new comment could be.
        // throws an exception if it's not in the file
        val sourceTextRange = documentable.textRange!!
        TextRange(sourceTextRange.startOffset, sourceTextRange.startOffset)
    } catch (_: Throwable) {
        return null
    }

    // calculate the indent of the doc comment by looking at how many spaces are on the first line before /**
    val docIndent = (docFileTextRange.startOffset -
            fileText.lastIndexOfNot('\n', docFileTextRange.startOffset)
            ).coerceAtLeast(0)

    // grab just the contents of the doc without the *-stuff
    val docContent = docFileTextRange.substring(fileText).getDocContentOrNull() ?: ""

    // Collect the imports from the file
    val imports = documentable.getImports().map { it.toSimpleImportPath() }

    // Get the raw source of the documentable
    val rawSource = documentable.text

    return DocumentableWrapper(
        docContent = docContent,
        programmingLanguage = documentable.programmingLanguage,
        imports = imports,
        rawSource = rawSource,
        fullyQualifiedPath = path,
        fullyQualifiedExtensionPath = extensionPath,
        file = file,
        docFileTextRange = docFileTextRange.toIntRange(),
        docIndent = docIndent,
    )
}