package nl.jolanrensen.docProcessor.gradle

import mu.KLogger
import org.gradle.api.logging.Logger

fun KLogger.lifecycle(msg: () -> Any?) {
    require(underlyingLogger is Logger) { "Logger must be org.gradle.api.logging.Logger for this." }
    val underlyingLogger = underlyingLogger as Logger

    if (underlyingLogger.isLifecycleEnabled) {
        underlyingLogger.lifecycle(msg().toString()) // unsafe!
    }
}

fun KLogger.lifecycle(throwable: Throwable, msg: () -> Any?) {
    require(underlyingLogger is Logger) { "Logger must be org.gradle.api.logging.Logger for this." }
    val underlyingLogger = underlyingLogger as Logger

    if (underlyingLogger.isLifecycleEnabled) {
        underlyingLogger.lifecycle(msg().toString(), throwable) // unsafe!
    }
}

/**
 * Logs the given message at lifecycle log level.
 *
 * @param message the log message.
 */
fun KLogger.lifecycle(message: String?) {
    require(underlyingLogger is Logger) { "Logger must be org.gradle.api.logging.Logger for this." }
    (underlyingLogger as Logger).lifecycle(message)
}

/**
 * Logs the given message at lifecycle log level.
 *
 * @param message the log message.
 * @param objects the log message parameters.
 */
fun KLogger.lifecycle(message: String?, vararg objects: Any?) {
    require(underlyingLogger is Logger) { "Logger must be org.gradle.api.logging.Logger for this." }
    (underlyingLogger as Logger).lifecycle(message, *objects)
}

/**
 * Logs the given message at lifecycle log level.
 *
 * @param message the log message.
 * @param throwable the exception to log.
 */
fun KLogger.lifecycle(message: String?, throwable: Throwable?) {
    require(underlyingLogger is Logger) { "Logger must be org.gradle.api.logging.Logger for this." }
    (underlyingLogger as Logger).lifecycle(message, throwable)
}
