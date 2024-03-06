package nl.jolanrensen.docProcessor

import kotlin.annotation.AnnotationTarget.*

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
annotation class ExportAsHtml(val theme: Boolean = true)
