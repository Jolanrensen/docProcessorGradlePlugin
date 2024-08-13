package nl.jolanrensen.docProcessor.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import nl.jolanrensen.docProcessor.MessageBundle
import nl.jolanrensen.docProcessor.Mode
import nl.jolanrensen.docProcessor.docProcessorIsEnabled
import nl.jolanrensen.docProcessor.mode
import javax.swing.JToggleButton

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

//        private val serviceK1 = toolWindow.project.service<DocProcessorServiceK1>()
//        private val serviceK2 = toolWindow.project.service<DocProcessorServiceK2>()

        private fun JToggleButton.updateState() {
            isSelected = true
            text = MessageBundle.message(
                if (docProcessorIsEnabled) "enabled" else "disabled",
            )
        }

        private fun JToggleButton.updateMode() {
            isSelected = true
            text = MessageBundle.message(mode.id)
        }

        fun getContent(): JBPanel<JBPanel<*>> =
            JBPanel<JBPanel<*>>().apply {
                add(JBLabel(MessageBundle.message("docPreprocessorEnabled")))
                add(
                    JToggleButton().apply {
                        updateState()
                        addActionListener {
                            docProcessorIsEnabled = !docProcessorIsEnabled
                            updateState()
                        }
                    },
                )

                add(JBLabel(MessageBundle.message("mode")))
                add(
                    JToggleButton().apply {
                        updateMode()
                        addActionListener {
                            mode = when (mode) {
                                Mode.K1 -> Mode.K2
                                Mode.K2 -> Mode.K1
                            }
                            updateMode()
                        }
                    },
                )
            }
    }
}
