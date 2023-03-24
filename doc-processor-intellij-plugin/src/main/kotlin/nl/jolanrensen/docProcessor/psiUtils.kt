package nl.jolanrensen.docProcessor

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.ImportPath

val PsiElement.docComment: PsiDocCommentBase?
    get() = when (this) {
        is KtDeclaration -> docComment
        is PsiDocCommentOwner -> docComment
        else -> error("Documentable must be a KtDeclaration or PsiDocCommentOwner, but was ${this::class.simpleName}")
    }

val PsiElement.programmingLanguage: ProgrammingLanguage
    get() = when (language) {
        is KotlinLanguage -> ProgrammingLanguage.KOTLIN
        is JavaLanguage -> ProgrammingLanguage.JAVA
        else -> error("Documentable must be using KotlinLanguage or JavaLanguage, but was ${language::class.simpleName}")
    }

fun PsiElement.getImports(): List<ImportPath> = buildList {
    when (this@getImports.language) {
        is JavaLanguage -> {
            val psiFile = containingFile as? PsiJavaFile

            val implicitImports = psiFile?.implicitlyImportedPackages?.toList().orEmpty()
            val writtenImports = psiFile
                ?.importList
                ?.allImportStatements
                ?.toList().orEmpty()

            for (import in implicitImports) {
                this += ImportPath(
                    fqName = FqName(import),
                    isAllUnder = true,
                )
            }

            for (import in writtenImports) {
                val qualifiedName = import.importReference?.qualifiedName ?: continue
                this += ImportPath(
                    fqName = FqName(qualifiedName),
                    isAllUnder = import.isOnDemand,
                )
            }
        }

        is KotlinLanguage -> {
            val writtenImports = containingFile
                .let { it as? KtFile }
                ?.importDirectives
                ?.mapNotNull { it.importPath }
                ?: emptyList()

            this += writtenImports

            val implicitImports = listOf(
                "kotlin",
                "kotlin.annotation",
                "kotlin.collections",
                "kotlin.comparisons",
                "kotlin.io",
                "kotlin.ranges",
                "kotlin.sequences",
                "kotlin.text",
                "kotlin.math",
            )

            for (import in implicitImports)
                this += ImportPath(
                    fqName = FqName(import),
                    isAllUnder = true,
                )
        }
    }
}


fun ImportPath.toSimpleImportPath(): SimpleImportPath = SimpleImportPath(
    fqName = fqName.toUnsafe().render(),
    isAllUnder = isAllUnder,
    alias = alias?.asString(),
)