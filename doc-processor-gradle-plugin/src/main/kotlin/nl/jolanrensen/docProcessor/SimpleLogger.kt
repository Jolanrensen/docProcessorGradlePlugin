package nl.jolanrensen.docProcessor

/**
 * Simple logger interface that has logEnabled property.
 */
interface SimpleLogger {

    val name: String
        get() = this::class.simpleName ?: "SimpleLogger"

    val logEnabled: Boolean

    private fun log(message: Any?) {
        if (logEnabled) kotlin.io.print("$name: $message")
    }

    /** Prints the given [message] to the standard output stream if [logEnabled]. */
    fun print(message: Any?) {
        log(message)
    }

    /** Prints the line separator to the standard output stream if [logEnabled]. */
    fun println() {
        log("\n")
    }

    /** Prints the given [message] and the line separator to the standard output stream if [logEnabled]. */
    fun println(message: Any?) {
        log("$message\n")
    }
}