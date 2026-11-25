package io.github.goomeui.goome.web

import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.github.goomeui.goome.GoomeSettings
import io.github.goomeui.goome.newproject.GoomeCli
import java.io.File

class WebLogToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(WebLog.get(project).console.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

object WebNotes {
    fun note(project: Project, text: String, type: NotificationType, vararg actions: NotificationAction): Notification {
        val n = NotificationGroupManager.getInstance().getNotificationGroup("goome.web").createNotification("goome", StringUtil.escapeXmlEntities(text), type)
        actions.forEach { n.addAction(it) }
        n.notify(project)
        return n
    }

    fun failed(project: Project, text: String) {
        WebLog.get(project).print(text, true)
        note(project, WebBuild.firstLine(text), NotificationType.ERROR, NotificationAction.createSimple("Show Full Output") { WebLog.get(project).show() })
    }

    fun built(project: Project, folder: File) {
        note(
            project, "Built for the web: ${folder.path}", NotificationType.INFORMATION,
            NotificationAction.createSimpleExpiring("Open in Browser") { openInBrowser(project, folder) },
            NotificationAction.createSimple("Show Folder") { RevealFileAction.openDirectory(folder) },
        )
    }

    fun openInBrowser(project: Project, folder: File) {
        object : Task.Backgroundable(project, "Starting goome web server", true) {
            var url: String? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val r = GoomeCli.findRunner()
                url = WebServer.get(project).start(r, folder)
                WebLog.get(project).print("serving ${folder.path} at $url")
            }

            override fun onSuccess() {
                url?.let { BrowserUtil.browse(it) }
            }

            override fun onThrowable(error: Throwable) {
                failed(project, error.message ?: error.toString())
            }
        }.queue()
    }
}

class WebBuildAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val inMenu = e.place == ActionPlaces.EDITOR_POPUP || e.place == ActionPlaces.PROJECT_VIEW_POPUP || e.place.contains("Popup")
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = e.project != null && (!inMenu || (file != null && !file.isDirectory && WebBuild.isC(file.name)))
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { !it.isDirectory && (WebBuild.isC(it.name) || WebBuild.languageOfFile(it.name) != null) }
        FileDocumentManager.getInstance().saveAllDocuments()
        start(project, file?.path)
    }

    companion object {
        fun startFromEditor(project: Project) {
            FileDocumentManager.getInstance().saveAllDocuments()
            val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull { !it.isDirectory && (WebBuild.isC(it.name) || WebBuild.languageOfFile(it.name) != null) }
            start(project, file?.path)
        }

        fun start(project: Project, file: String?) {
            val base = File(project.basePath ?: File(file ?: ".").absoluteFile.parent)
            val goome = GoomeSettings.get().goomeSrc
            val log = WebLog.get(project)
            object : Task.Backgroundable(project, "Building for the web", true) {
                var result: WebBuild.Result = WebBuild.Result.Cancelled

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "finding goome-cli"
                    val r = GoomeCli.findRunner()
                    indicator.text = "goome build --web"
                    log.clear()
                    log.print("goome build --web ${file ?: ""}".trim())
                    result = WebBuild.build(r, file, base, goome, indicator)
                }

                override fun onSuccess() {
                    when (val res = result) {
                        is WebBuild.Result.Ok -> {
                            if (res.text.isNotEmpty()) log.print(res.text)
                            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(res.folder)
                            WebNotes.built(project, res.folder)
                        }
                        is WebBuild.Result.Failed -> WebNotes.failed(project, res.text)
                        WebBuild.Result.Cancelled -> log.print("cancelled")
                    }
                }

                override fun onThrowable(error: Throwable) {
                    WebNotes.failed(project, error.message ?: error.toString())
                }
            }.queue()
        }
    }
}

class StopWebServerAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val p = e.project
        e.presentation.isEnabledAndVisible = p != null
        e.presentation.isEnabled = p != null && WebServer.get(p).running()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (WebServer.get(project).stop()) WebNotes.note(project, "Web server stopped", NotificationType.INFORMATION)
    }
}
