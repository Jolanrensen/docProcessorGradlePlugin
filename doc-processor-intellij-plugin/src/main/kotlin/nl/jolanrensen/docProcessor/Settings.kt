package nl.jolanrensen.docProcessor

import com.intellij.ide.util.PropertiesComponent
import org.jetbrains.annotations.PropertyKey
import kotlin.enums.EnumEntries

private const val MODE = "docProcessor.mode"
private const val ENABLED = "docProcessor.enabled"

private const val HIGHLIGHTING = "docProcessor.highlighting"
private const val COMPLETION = "docProcessor.completion"

enum class Mode(val id: String) {
    K1("k1"),
    K2("k2"),
}

sealed interface Setting<T> {
    val messageBundleName: String
    val key: String
    val default: T

    fun T.asString(): String

    fun String.asType(): T

    var value: T
        get() = PropertiesComponent.getInstance().getValue(key, default.asString()).asType()
        set(value) {
            PropertiesComponent.getInstance().setValue(key, value.asString())
        }

    operator fun getValue(thisRef: Any?, property: Any?): T = value

    operator fun setValue(thisRef: Any?, property: Any?, value: T) {
        this.value = value
    }
}

sealed interface BooleanSetting : Setting<Boolean> {
    override fun Boolean.asString(): String = toString()

    override fun String.asType(): Boolean = toBoolean()
}

sealed interface EnumSetting<T : Enum<T>> : Setting<T> {
    val values: EnumEntries<T>

    fun setValueAsAny(value: Any) {
        this.value = value as T
    }
}

data object PreprocessorMode : EnumSetting<Mode> {
    override val values: EnumEntries<Mode>
        get() = Mode.entries

    @PropertyKey(resourceBundle = "messages.MessageBundle")
    override val messageBundleName: String = "mode"
    override val key: String = MODE
    override val default: Mode = Mode.K2

    override fun Mode.asString(): String = name

    override fun String.asType(): Mode = Mode.valueOf(this)
}

data object DocProcessorIsEnabled : BooleanSetting {
    @PropertyKey(resourceBundle = "messages.MessageBundle")
    override val messageBundleName: String = "docPreprocessorEnabled"
    override val key: String = ENABLED
    override val default: Boolean = true
}

data object DocProcessorHighlightingIsEnabled : BooleanSetting {
    @PropertyKey(resourceBundle = "messages.MessageBundle")
    override val messageBundleName: String = "docProcessorHighlightingEnabled"
    override val key: String = HIGHLIGHTING
    override val default: Boolean = true
}

data object DocProcessorCompletionIsEnabled : BooleanSetting {
    @PropertyKey(resourceBundle = "messages.MessageBundle")
    override val messageBundleName: String = "docProcessorCompletionEnabled"
    override val key: String = COMPLETION
    override val default: Boolean = true
}

val allSettings: Array<Setting<*>> = arrayOf(
    PreprocessorMode,
    DocProcessorIsEnabled,
    DocProcessorHighlightingIsEnabled,
    DocProcessorCompletionIsEnabled,
)

var preprocessorMode by PreprocessorMode
var docProcessorIsEnabled by DocProcessorIsEnabled
var docProcessorHighlightingIsEnabled by DocProcessorHighlightingIsEnabled
var docProcessorCompletionIsEnabled by DocProcessorCompletionIsEnabled
