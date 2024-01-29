package nl.jolanrensen.docProcessor

import kotlin.annotation.AnnotationTarget.*

/**
 * Example `ExcludeFromSources` annotation.
 *
 * Any `Documentable` annotated with this annotation will be excluded from the generated sources by
 * the documentation processor.
 */
@Target(
    CLASS,
    ANNOTATION_CLASS,
    PROPERTY,
    FIELD,
    LOCAL_VARIABLE,
    VALUE_PARAMETER,
    CONSTRUCTOR,
    FUNCTION,
    PROPERTY_GETTER,
    PROPERTY_SETTER,
    TYPE,
    TYPEALIAS,
    FILE,
)
annotation class ExcludeFromSources
