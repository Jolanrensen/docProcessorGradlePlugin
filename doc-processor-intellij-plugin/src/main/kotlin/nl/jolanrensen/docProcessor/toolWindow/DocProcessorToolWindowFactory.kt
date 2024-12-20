package nl.jolanrensen.docProcessor.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import nl.jolanrensen.docProcessor.BooleanSetting
import nl.jolanrensen.docProcessor.EnumSetting
import nl.jolanrensen.docProcessor.MessageBundle
import nl.jolanrensen.docProcessor.allSettings
import nl.jolanrensen.docProcessor.getLoadedProcessors
import nl.jolanrensen.docProcessor.services.DocProcessorServiceK2
import javax.swing.JComponent

class DocProcessorToolWindowFactory : ToolWindowFactory {

    private val contentFactory = ContentFactory.getInstance()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.contentManager.addContent(
            @Suppress("ktlint:standard:comment-wrapping")
            contentFactory.createContent(
                /* component = */ DocProcessorToolWindow(toolWindow).getContent(),
                /* displayName = */ null,
                /* isLockable = */ false,
            ),
        )
    }

    override fun shouldBeAvailable(project: Project) = true

    class DocProcessorToolWindow(toolWindow: ToolWindow) {
        fun getContent(): JComponent =
            panel {
                indent {
                    group(MessageBundle.message("settings")) {
                        for (setting in allSettings) {
                            row {
                                when (setting) {
                                    is BooleanSetting ->
                                        checkBox(MessageBundle.message(setting.messageBundleName))
                                            .bindSelected(setting::value)

                                    is EnumSetting<*> -> {
                                        label(MessageBundle.message(setting.messageBundleName))
                                        comboBox(setting.values).bindItem({ setting.value }) {
                                            it?.let { setting.setValueAsAny(it) }
                                        }
                                    }
                                }
                            }
                        }
                        row { text(MessageBundle.message("changeSettings")) }
                    }

                    group(MessageBundle.message("loadedPreprocessors")) {
                        val loadedPreprocessors = DocProcessorServiceK2::class.java.classLoader.getLoadedProcessors()

                        if (loadedPreprocessors.isEmpty()) row(MessageBundle.message("noPreprocessorsLoaded")) {}
                        for ((i, preProcessor) in loadedPreprocessors.withIndex()) {
                            row("${i + 1}. ${preProcessor.name}") {}
                        }

                        row { text(MessageBundle.message("loadedPreprocessorsMessage")) }
                    }
                }
            }
    }
}
