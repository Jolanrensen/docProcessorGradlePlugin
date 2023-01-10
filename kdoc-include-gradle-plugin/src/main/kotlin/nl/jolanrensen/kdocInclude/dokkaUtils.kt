package nl.jolanrensen.kdocInclude

import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.model.Callable
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.doc.TagWrapper

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
fun Documentable.hasDocumentation(sourceSet: DokkaConfiguration.DokkaSourceSet): Boolean = documentation[sourceSet]?.children?.isNotEmpty() ?: false

fun Documentable.allDocumentation(): List<TagWrapper> = documentation.flatMap { it.value.children }

