package nl.jolanrensen.docProcessor

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.thisLogger


private const val MODE_KEY = "docProcessor.mode"
private const val ENABLED_KEY = "docProcessor.enabled"

enum class Mode(val id: String) {
    K1("k1"),
    K2("k2"),
}

object Settings {
    val logger = thisLogger()
}

var mode: Mode
    get() = Mode.valueOf(
        PropertiesComponent.getInstance()
            .getValue(MODE_KEY, Mode.K1.name)
    )
    set(value) {
        PropertiesComponent.getInstance()
            .setValue(MODE_KEY, value.name)
        println("Mode set to $value")
    }

var docProcessorIsEnabled: Boolean
    get() = PropertiesComponent.getInstance()
        .getValue(ENABLED_KEY, "true").toBoolean()
    set(value) {
        PropertiesComponent.getInstance()
            .setValue(ENABLED_KEY, value.toString())
        println("DocProcessor enabled set to $value")
    }
