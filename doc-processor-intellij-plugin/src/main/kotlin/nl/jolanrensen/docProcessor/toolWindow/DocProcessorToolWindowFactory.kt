package nl.jolanrensen.docProcessor.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import nl.jolanrensen.docProcessor.MessageBundle
import nl.jolanrensen.docProcessor.services.DocProcessorServiceK1
import nl.jolanrensen.docProcessor.services.DocProcessorServiceK2
import javax.swing.JToggleButton

class DocProcessorToolWindowFactory : ToolWindowFactory {
    init {
//        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    private val contentFactory = ContentFactory.getInstance()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.contentManager.addContent(
            contentFactory.createContent(
                /* component = */ DocProcessorToolWindow(toolWindow).getContent(),
                /* displayName = */ null,
                /* isLockable = */ false,
            )
        )
    }

    override fun shouldBeAvailable(project: Project) = true

    class DocProcessorToolWindow(toolWindow: ToolWindow) {

        private val serviceK1 = toolWindow.project.service<DocProcessorServiceK1>()
        private val serviceK2 = toolWindow.project.service<DocProcessorServiceK2>()

        private fun JToggleButton.updateStateK1() {
            isSelected = serviceK1.isEnabled
            text = MessageBundle.message(
                if (serviceK1.isEnabled) "k1enabled" else "k1disabled",
            )
        }

        private fun JToggleButton.updateStateK2() {
            isSelected = serviceK2.isEnabled
            text = MessageBundle.message(
                if (serviceK2.isEnabled) "k2enabled" else "k2disabled",
            )
        }

        fun getContent(): JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>().apply {
            add(JBLabel(MessageBundle.message("docPreprocessorEnabled")))
            add(JToggleButton().apply {
                updateStateK1()
                addActionListener {
                    serviceK1.isEnabled = !serviceK1.isEnabled
                    updateStateK1()
                }
            })
            add(JToggleButton().apply {
                updateStateK2()
                addActionListener {
                    serviceK2.isEnabled = !serviceK2.isEnabled
                    updateStateK2()
                }
            })
        }
    }
}
