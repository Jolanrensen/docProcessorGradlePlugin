package nl.jolanrensen.docProcessor

/**
 * Many of the contents of this file are copied from Dokka's
 * [DocComment][org.jetbrains.dokka.base.translators.psi.parsers.DocComment]
 * since many of these methods were internal or private.
 * However, there's no other way to access JavaDoc than with [findClosestDocComment].
 * See https://github.com/Kotlin/dokka/blob/master/plugins/base/src/main/kotlin/translators/psi/parsers/PsiCommentsUtils.kt
 */

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.JavaDocElementType
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.dokka.analysis.DescriptorDocumentableSource
import org.jetbrains.dokka.analysis.PsiDocumentableSource
import org.jetbrains.dokka.analysis.from
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.JavaClassReference
import org.jetbrains.dokka.links.Nullable
import org.jetbrains.dokka.links.TypeConstructor
import org.jetbrains.dokka.links.TypeReference
import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.utilities.DokkaLogger
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.utils.fqname.getKotlinFqName
import org.jetbrains.kotlin.idea.kdoc.findKDoc
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.hasComments
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.*

/**
 * Is [linkable element](https://kotlinlang.org/docs/kotlin-doc.html#links-to-elements)
 *
 * @receiver [Documentable]
 * @return true if receiver is linkable element
 */
fun Documentable.isLinkableElement(): Boolean =
    this is DClasslike ||
            this is Callable ||
            this is DParameter ||
            this is DTypeAlias // TODO: issue #12: will not be included in DocumentableWithSources since it has no source

sealed interface DocComment {
    fun hasTag(tag: JavadocTag): Boolean
    fun hasTagWithExceptionOfType(tag: JavadocTag, exceptionFqName: String): Boolean
    fun tagsByName(tag: JavadocTag, param: String? = null): List<DocumentationContent>

    val tagNames: List<String>
}

/**
 * Get Kdoc content. Note! This doesn't include the @ Kdoc tags.
 *
 * @receiver [DocComment]
 * @return
 */
val DocComment.documentString: String?
    get() = when (this) {
        is JavaDocComment -> comment.text
        is KotlinDocComment -> comment.text
    }.getDocContentOrNull()

/**
 * Get text range of Kdoc/JavaDoc comment from /** to */
 *
 * @receiver [DocComment]
 * @return [TextRange]
 */
val DocComment.textRange: TextRange
    get() = when (this) {
        is JavaDocComment -> comment.textRange
        is KotlinDocComment -> comment.parent.textRange
    }

/**
 * Gets the [PsiNamedElement] from the [DocumentableSource] if it can find it.
 */
val DocumentableSource.psi: PsiNamedElement?
    get() = when (this) {
        is PsiDocumentableSource -> psi
        is DescriptorDocumentableSource -> descriptor.findPsi() as PsiNamedElement?
        else -> null
    }

/**
 * Get the text range in the file for this [DocumentableSource].
 */
val DocumentableSource.textRange: TextRange?
    get() = psi?.textRange

data class JavaDocComment(val comment: PsiDocComment) : DocComment {
    override fun hasTag(tag: JavadocTag): Boolean = comment.hasTag(tag)
    override fun hasTagWithExceptionOfType(tag: JavadocTag, exceptionFqName: String): Boolean =
        comment.hasTag(tag) && comment.tagsByName(tag).firstIsInstanceOrNull<PsiDocTag>()
            ?.resolveToElement()
            ?.getKotlinFqName()?.asString() == exceptionFqName

    override fun tagsByName(tag: JavadocTag, param: String?): List<DocumentationContent> =
        comment.tagsByName(tag).map { PsiDocumentationContent(it, tag) }

    override val tagNames: List<String>
        get() = comment.tags.map { it.name }
}

data class KotlinDocComment(val comment: KDocTag, val descriptor: DeclarationDescriptor?) : DocComment {

    override fun hasTag(tag: JavadocTag): Boolean =
        when (tag) {
            JavadocTag.DESCRIPTION -> comment.getContent().isNotEmpty()
            else -> tagsWithContent.any { it.text.startsWith("@$tag") }
        }

    override fun hasTagWithExceptionOfType(tag: JavadocTag, exceptionFqName: String): Boolean =
        tagsWithContent.any { it.hasExceptionWithName(tag, exceptionFqName) }

    override fun tagsByName(tag: JavadocTag, param: String?): List<DocumentationContent> =
        when (tag) {
            JavadocTag.DESCRIPTION -> listOf(DescriptorDocumentationContent(descriptor, comment, tag))
            else -> comment.children.mapNotNull { (it as? KDocTag) }
                .filter { it.name == "$tag" && param?.let { param -> it.hasExceptionWithName(param) } != false }
                .map { DescriptorDocumentationContent(descriptor, it, tag) }
        }

    override val tagNames: List<String>
        get() = tagsWithContent.mapNotNull { it.name }

    private val tagsWithContent: List<KDocTag> = comment.children.mapNotNull { (it as? KDocTag) }

    private fun KDocTag.hasExceptionWithName(tag: JavadocTag, exceptionFqName: String) =
        text.startsWith("@$tag") && hasExceptionWithName(exceptionFqName)

    private fun KDocTag.hasExceptionWithName(exceptionFqName: String) =
        getSubjectName() == exceptionFqName
}

interface DocumentationContent {
    val tag: JavadocTag
}

data class PsiDocumentationContent(val psiElement: PsiElement, override val tag: JavadocTag) :
    DocumentationContent

data class DescriptorDocumentationContent(
    val descriptor: DeclarationDescriptor?,
    val element: KDocTag,
    override val tag: JavadocTag,
) : DocumentationContent

fun PsiDocComment.hasTag(tag: JavadocTag): Boolean =
    when (tag) {
        JavadocTag.DESCRIPTION -> descriptionElements.isNotEmpty()
        else -> findTagByName(tag.toString()) != null
    }

fun PsiDocComment.tagsByName(tag: JavadocTag): List<PsiElement> =
    when (tag) {
        JavadocTag.DESCRIPTION -> descriptionElements.toList()
        else -> findTagsByName(tag.toString()).toList()
    }

enum class JavadocTag {
    PARAM, THROWS, RETURN, AUTHOR, SEE, DEPRECATED, EXCEPTION, HIDE, INCLUDE,

    /**
     * Artificial tag created to handle tag-less section
     */
    DESCRIPTION,
    ;

    override fun toString(): String = super.toString().lowercase()

    /* Missing tags:
        SERIAL,
        SERIAL_DATA,
        SERIAL_FIELD,
        SINCE,
        VERSION
     */
}

fun PsiClass.implementsInterface(fqName: FqName): Boolean {
    return allInterfaces().any { it.getKotlinFqName() == fqName }
}

fun PsiClass.allInterfaces(): Sequence<PsiClass> {
    return sequence {
        this.yieldAll(interfaces.toList())
        interfaces.forEach { yieldAll(it.allInterfaces()) }
    }
}

/**
 * Workaround for failing [PsiMethod.findSuperMethods].
 * This might be resolved once ultra light classes are enabled for dokka
 * See [KT-39518](https://youtrack.jetbrains.com/issue/KT-39518)
 */
fun PsiMethod.findSuperMethodsOrEmptyArray(logger: DokkaLogger): Array<PsiMethod> {
    return try {
        /*
        We are not even attempting to call "findSuperMethods" on all methods called "getGetter" or "getSetter"
        on any object implementing "kotlin.reflect.KProperty", since we know that those methods will fail
        (KT-39518). Just catching the exception is not good enough, since "findSuperMethods" will
        print the whole exception to stderr internally and then spoil the console.
         */
        val kPropertyFqName = FqName("kotlin.reflect.KProperty")
        if (
            this.parent?.safeAs<PsiClass>()?.implementsInterface(kPropertyFqName) == true &&
            (this.name == "getSetter" || this.name == "getGetter")
        ) {
            logger.warn("Skipped lookup of super methods for ${getKotlinFqName()} (KT-39518)")
            return emptyArray()
        }
        findSuperMethods()
    } catch (exception: Throwable) {
        logger.warn("Failed to lookup of super methods for ${getKotlinFqName()} (KT-39518)")
        emptyArray()
    }
}

fun findClosestDocComment(element: PsiNamedElement?, logger: DokkaLogger): DocComment? {
    if (element == null) return null
    (element as? PsiDocCommentOwner)?.docComment?.run { return@findClosestDocComment JavaDocComment(this) }
    element.toKdocComment()?.run { return@findClosestDocComment this }

    if (element is PsiMethod) {
        val superMethods = element.findSuperMethodsOrEmptyArray(logger)
        if (superMethods.isEmpty()) return null

        if (superMethods.size == 1) {
            return findClosestDocComment(superMethods.single(), logger)
        }

        val superMethodDocumentation = superMethods.map { method -> findClosestDocComment(method, logger) }.distinct()
        if (superMethodDocumentation.size == 1) {
            return superMethodDocumentation.single()
        }

        logger.debug(
            "Conflicting documentation for ${DRI.from(element)}" +
                    "${superMethods.map { DRI.from(it) }}"
        )

        /* Prioritize super class over interface */
        val indexOfSuperClass = superMethods.indexOfFirst { method ->
            val parent = method.parent
            if (parent is PsiClass) !parent.isInterface
            else false
        }

        return if (indexOfSuperClass >= 0) superMethodDocumentation[indexOfSuperClass]
        else superMethodDocumentation.first()
    }
    return element.children.firstIsInstanceOrNull<PsiDocComment>()?.let { JavaDocComment(it) }
}

fun PsiNamedElement.toKdocComment(): KotlinDocComment? =
    if (!hasComments()) {
        null
    } else {
        (navigationElement as? KtElement)?.findKDoc {
            // NOTE: This returns the wrong file if named the same and no comment was found!
            DescriptorToSourceUtils.descriptorToDeclaration(it)
        }?.run {
            KotlinDocComment(
                comment = this,
                descriptor = (this@toKdocComment.navigationElement as? KtDeclaration)?.descriptor,
            )
        }
    }

fun PsiDocTag.resolveToElement(): PsiElement? =
    dataElements.firstOrNull()?.firstChild?.referenceElementOrSelf()?.resolveToGetDri()

fun PsiElement.referenceElementOrSelf(): PsiElement? =
    if (node.elementType == JavaDocElementType.DOC_REFERENCE_HOLDER) {
        PsiTreeUtil.findChildOfType(this, PsiJavaCodeReferenceElement::class.java)
    } else this

fun PsiElement.resolveToGetDri(): PsiElement? =
    reference?.resolve()

/**
 * Gets the fully qualified path of a linkable target.
 * If it's an extension function/property, the receiver is ignored.
 */
val DRI.fullyQualifiedPath: String
    get() = listOf(
        packageName?.split('.').orEmpty(),
        classNames?.split('.').orEmpty(),
        listOfNotNull(callable?.name),
    ).flatten().joinToString(".")

/**
 * Gets the fully qualified path of a linkable target that is an extension
 * function/property from the perspective of the receiver. So
 * `com.something.Receiver.extension`
 */
val DRI.fullyQualifiedExtensionPath: String?
    get() = callable?.receiver?.let { receiver ->
        listOf(
            receiver.path.split('.'),
            listOf(callable!!.name),
        ).flatten().joinToString(".")
    }

val DRI.paths: List<String>
    get() = listOfNotNull(fullyQualifiedPath, fullyQualifiedExtensionPath)

val TypeReference.path: String
    get() = when (this) {
        is TypeConstructor -> fullyQualifiedName
        is JavaClassReference -> name
        is Nullable -> wrapped.path
        else -> toString()
    }

val ImportPath.hasStar: Boolean
    get() = isAllUnder

val DocumentableSource.programmingLanguage: ProgrammingLanguage
    get() = when (this) {
        is PsiDocumentableSource -> ProgrammingLanguage.JAVA
        is DescriptorDocumentableSource -> ProgrammingLanguage.KOTLIN
        else -> error("Unknown source type: ${this::class.simpleName}")
    }

/**
 * Collects the imports from this [DocumentableSource] as [ImportPath]s, irrespective of the programming language.
 */
fun DocumentableSource.getImports(): List<ImportPath> = buildList {
    when (programmingLanguage) {
        ProgrammingLanguage.JAVA -> {
            val psiFile = psi?.containingFile as? PsiJavaFile

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

        ProgrammingLanguage.KOTLIN -> {
            val writtenImports = psi
                ?.containingFile
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

fun AnnotationParameterValue.getValue(): Any? =
    when (this) {
        is StringValue -> value
        is BooleanValue -> value
        is NullValue -> null
        is DoubleValue -> value
        is FloatValue -> value
        is LongValue -> value
        is IntValue -> value
        is LiteralValue -> text()
        is ClassValue -> classDRI.fullyQualifiedPath
        is EnumValue -> enumDri.fullyQualifiedPath
        is ArrayValue -> value.map { it.getValue() }
        is AnnotationValue -> AnnotationWrapper(
            fullyQualifiedPath = annotation.dri.fullyQualifiedPath,
            arguments = annotation.params.entries.map { (name, paramValue) ->
                name to paramValue.getValue()
            },
        )
        else -> error("Could not read Annotation parameter: $this")
    }