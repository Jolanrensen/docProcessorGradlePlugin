package nl.jolanrensen.docProcessor.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import nl.jolanrensen.docProcessor.MessageBundle
import nl.jolanrensen.docProcessor.services.DocProcessorService
import javax.swing.JButton

//class DocProcessorToolWindowFactory : ToolWindowFactory {
//    init {
//        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
//    }
//
//    private val contentFactory = ContentFactory.SERVICE.getInstance()
//
//    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
//        toolWindow.contentManager.addContent(
//            contentFactory.createContent(
//                /* component = */ DocProcessorToolWindow(toolWindow).getContent(),
//                /* displayName = */ null,
//                /* isLockable = */ false,
//            )
//        )
//    }
//
//    override fun shouldBeAvailable(project: Project) = true
//
//    class DocProcessorToolWindow(toolWindow: ToolWindow) {
//
//        private val service = toolWindow.project.service<DocProcessorService>()
//
//        fun getContent(): JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>().apply {
//            val label = JBLabel(MessageBundle.message("randomLabel", "?"))
//
//            add(label)
//            add(JButton(MessageBundle.message("shuffle")).apply {
//                addActionListener {
//                    label.text = MessageBundle.message("randomLabel", service.getRandomNumber())
//                }
//            })
//        }
//    }
//}