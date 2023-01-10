package nl.jolanrensen.kdocInclude

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
import org.jetbrains.dokka.analysis.from
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.utilities.DokkaLogger
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.utils.fqname.getKotlinFqName
import org.jetbrains.kotlin.idea.kdoc.findKDoc
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


sealed interface DocComment {
    fun hasTag(tag: JavadocTag): Boolean
    fun hasTagWithExceptionOfType(tag: JavadocTag, exceptionFqName: String): Boolean
    fun tagsByName(tag: JavadocTag, param: String? = null): List<DocumentationContent>
}

internal fun DocComment.getDocumentString(): String = when (this) {
    is JavaDocComment -> comment.text//children.joinToString("") { it.text }
    is KotlinDocComment -> comment.text
}.getKdocContent()

internal data class JavaDocComment(val comment: PsiDocComment) : DocComment {
    override fun hasTag(tag: JavadocTag): Boolean = comment.hasTag(tag)
    override fun hasTagWithExceptionOfType(tag: JavadocTag, exceptionFqName: String): Boolean =
        comment.hasTag(tag) && comment.tagsByName(tag).firstIsInstanceOrNull<PsiDocTag>()
            ?.resolveToElement()
            ?.getKotlinFqName()?.asString() == exceptionFqName

    override fun tagsByName(tag: JavadocTag, param: String?): List<DocumentationContent> =
        comment.tagsByName(tag).map { PsiDocumentationContent(it, tag) }
}

internal data class KotlinDocComment(val comment: KDocTag, val descriptor: DeclarationDescriptor) : DocComment {

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
    val descriptor: DeclarationDescriptor,
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
    PARAM, THROWS, RETURN, AUTHOR, SEE, DEPRECATED, EXCEPTION, HIDE,

    /**
     * Artificial tag created to handle tag-less section
     */
    DESCRIPTION, ;

    override fun toString(): String = super.toString().toLowerCase()

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
    (navigationElement as? KtElement)?.findKDoc { DescriptorToSourceUtils.descriptorToDeclaration(it) }
        ?.run {
            (this@toKdocComment.navigationElement as? KtDeclaration)?.descriptor?.let {
                KotlinDocComment(
                    this,
                    it
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

fun DRI.asLinkString() = listOfNotNull(packageName, classNames, callable?.name)
    .joinToString(".")



