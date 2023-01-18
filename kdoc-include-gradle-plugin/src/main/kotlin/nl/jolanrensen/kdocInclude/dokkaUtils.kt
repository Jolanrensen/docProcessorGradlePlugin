package nl.jolanrensen.kdocInclude

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.tree.JavaDocElementType
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.analysis.DescriptorDocumentableSource
import org.jetbrains.dokka.analysis.PsiDocumentableSource
import org.jetbrains.dokka.analysis.from
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.Callable
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.model.doc.TagWrapper
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
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.*

/**
 * Many of the contents of this file are copied from Dokka's
 * [org.jetbrains.dokka.base.translators.psi.parsers.DocComment]
 * since many of these methods were internal or private.
 * However, there's no other way to access JavaDoc than with [findClosestDocComment].
 * See https://github.com/Kotlin/dokka/blob/master/plugins/base/src/main/kotlin/translators/psi/parsers/PsiCommentsUtils.kt
 */

/**
 * Is [linkable element](https://kotlinlang.org/docs/kotlin-doc.html#links-to-elements)
 *
 * @receiver [Documentable]
 * @return true if receiver is linkable element
 */
fun Documentable.isLinkableElement(): Boolean = this is DClasslike || this is Callable || this is DParameter

/**
 * Has documentation for any sourceSet
 *
 * @receiver [Documentable]
 * @return true if receiver has documentation for any sourceSet
 */
fun Documentable.hasDocumentation(): Boolean = allDocumentation().isNotEmpty()

/**
 * Has documentation for given [sourceSet]
 *
 * @receiver [Documentable]
 * @return true if receiver has documentation for any sourceSet
 */
fun Documentable.hasDocumentation(sourceSet: DokkaConfiguration.DokkaSourceSet): Boolean =
    documentation[sourceSet]?.children?.isNotEmpty() ?: false

fun Documentable.allDocumentation(): List<TagWrapper> = documentation.flatMap { it.value.children }


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
internal val DocComment.documentString: String
    get() = when (this) {
        is JavaDocComment -> comment.text
        is KotlinDocComment -> comment.text
    }.getKdocContent()

/**
 * Get text range of Kdoc/JavaDoc comment from /** to */
 *
 * @receiver [DocComment]
 * @return [TextRange]
 */
internal val DocComment.textRange: TextRange
    get() = when (this) {
        is JavaDocComment -> comment.textRange
        is KotlinDocComment -> comment.parent.textRange
    }

internal val DocumentableSource.psi: PsiNamedElement?
    get() = when (this) {
        is PsiDocumentableSource -> psi
        is DescriptorDocumentableSource -> descriptor.findPsi() as PsiNamedElement?
        else -> null
    }


internal data class JavaDocComment(val comment: PsiDocComment) : DocComment {
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

internal data class KotlinDocComment(val comment: KDocTag, val descriptor: DeclarationDescriptor?) : DocComment {

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

internal data class PsiDocumentationContent(val psiElement: PsiElement, override val tag: JavadocTag) :
    DocumentationContent

internal data class DescriptorDocumentationContent(
    val descriptor: DeclarationDescriptor?,
    val element: KDocTag,
    override val tag: JavadocTag
) : DocumentationContent

internal fun PsiDocComment.hasTag(tag: JavadocTag): Boolean =
    when (tag) {
        JavadocTag.DESCRIPTION -> descriptionElements.isNotEmpty()
        else -> findTagByName(tag.toString()) != null
    }

internal fun PsiDocComment.tagsByName(tag: JavadocTag): List<PsiElement> =
    when (tag) {
        JavadocTag.DESCRIPTION -> descriptionElements.toList()
        else -> findTagsByName(tag.toString()).toList()
    }

enum class JavadocTag {
    PARAM, THROWS, RETURN, AUTHOR, SEE, DEPRECATED, EXCEPTION, HIDE, INCLUDE,

    /**
     * Artificial tag created to handle tag-less section
     */
    DESCRIPTION, ;

    override fun toString(): String = super.toString().lowercase()

    /* Missing tags:
        SERIAL,
        SERIAL_DATA,
        SERIAL_FIELD,
        SINCE,
        VERSION
     */
}

internal fun PsiClass.implementsInterface(fqName: FqName): Boolean {
    return allInterfaces().any { it.getKotlinFqName() == fqName }
}

internal fun PsiClass.allInterfaces(): Sequence<PsiClass> {
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
internal fun PsiMethod.findSuperMethodsOrEmptyArray(logger: DokkaLogger): Array<PsiMethod> {
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

internal fun findClosestDocComment(element: PsiNamedElement?, logger: DokkaLogger): DocComment? {
    if (element == null) return null
    (element as? PsiDocCommentOwner)?.docComment?.run { return JavaDocComment(this) }
    element.toKdocComment()?.run { return this }

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

internal fun PsiNamedElement.toKdocComment(): KotlinDocComment? =
    if (!hasComments()) {
        null
    } else {
        (navigationElement as? KtElement)?.findKDoc {
            // TODO This returns the wrong file if named the same!
            DescriptorToSourceUtils.descriptorToDeclaration(it)
        }?.run {
            KotlinDocComment(
                comment = this,
                descriptor = (this@toKdocComment.navigationElement as? KtDeclaration)?.descriptor,
            )
        }
    }

internal fun PsiDocTag.resolveToElement(): PsiElement? =
    dataElements.firstOrNull()?.firstChild?.referenceElementOrSelf()?.resolveToGetDri()

internal fun PsiElement.referenceElementOrSelf(): PsiElement? =
    if (node.elementType == JavaDocElementType.DOC_REFERENCE_HOLDER) {
        PsiTreeUtil.findChildOfType(this, PsiJavaCodeReferenceElement::class.java)
    } else this

internal fun PsiElement.resolveToGetDri(): PsiElement? =
    reference?.resolve()

val DRI.path: String
    get() = (
            packageName?.split('.').orEmpty() +
                    classNames?.split('.').orEmpty() +
                    listOfNotNull(callable?.name)
            ).joinToString(".")