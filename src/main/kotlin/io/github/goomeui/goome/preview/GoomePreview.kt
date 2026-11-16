package io.github.goomeui.goome.preview

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.Alarm
import io.github.goomeui.goome.Goome
import java.awt.datatransfer.StringSelection
import java.beans.PropertyChangeListener
import javax.swing.JComponent

object PreviewPage {
    val CODE_LANGS = linkedMapOf("c" to "C", "cs" to "C#", "py" to "Python", "java" to "Java", "gd" to "GDScript")
    val SCRIPTS = listOf("model.js", "codegen.js", "elements.js", "app.js", "stage.js", "vars.js")
    val STYLES = listOf("builder/style.css", "builder/elements.css")

    private fun res(path: String): String =
        PreviewPage::class.java.getResourceAsStream(path)?.use { String(it.readAllBytes(), Charsets.UTF_8) }
            ?: throw IllegalStateException("$path is missing from the plugin")

    private fun script(text: String) = "<script>" + text.replace("</script", "<\\/script") + "</script>"

    fun html(sendJs: String): String {
        val sb = StringBuilder()
        sb.append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        for (s in STYLES) sb.append("<style>").append(res("/goome/$s")).append("</style>\n")
        sb.append("<style>").append(res("/preview/preview.css")).append("</style>\n")
        sb.append("</head>\n<body>\n<div class=\"app gp\">\n  <header class=\"top\">\n")
        sb.append("    <div class=\"brand\"><i></i><span id=\"gpName\">goome ui</span> <small>preview</small></div>\n")
        sb.append("    <div class=\"sp\"></div>\n    <div class=\"gp-copy\" role=\"group\" aria-label=\"Copy code\">\n      <span>Copy code</span>\n")
        for ((id, label) in CODE_LANGS) sb.append("      <button class=\"btn sm\" data-copy=\"$id\" title=\"Copy the $label code\">$label</button>\n")
        sb.append("    </div>\n    <button class=\"btn sm pri\" id=\"gpBuilder\" title=\"${Goome.BUILDER_URL}\">Open in UI builder</button>\n  </header>\n")
        sb.append("  <div class=\"stage-wrap\" id=\"stageWrap\"></div>\n  <div class=\"gp-err\" id=\"gpErr\" hidden></div>\n</div>\n")
        sb.append("<div hidden><button id=\"undoBtn\"></button><button id=\"redoBtn\"></button></div>\n")
        sb.append(script("window.goomeSend = function (m) { $sendJs };")).append("\n")
        for (s in SCRIPTS) sb.append(script(res("/goome/builder/$s"))).append("\n")
        sb.append(script(res("/preview/preview.js"))).append("\n</body>\n</html>\n")
        return sb.toString()
    }
}

class GoomePreview(private val project: Project, private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val browser = JBCefBrowser()
    private val query = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val gson = Gson()
    var nodes = 0
        private set
    var error = ""
        private set

    init {
        Disposer.register(this, browser)
        Disposer.register(this, query)
        query.addHandler { msg ->
            ApplicationManager.getApplication().invokeLater { handle(msg) }
            null
        }
        FileDocumentManager.getInstance().getDocument(file)?.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                alarm.cancelAllRequests()
                alarm.addRequest({ send() }, 200)
            }
        }, this)
        browser.loadHTML(PreviewPage.html(query.inject("m")))
    }

    private fun send() {
        if (project.isDisposed) return
        val text = ReadAction.compute<String?, RuntimeException> { FileDocumentManager.getInstance().getDocument(file)?.text } ?: return
        val js = "window.goomeLoad && window.goomeLoad(" + gson.toJson(text) + ", " + gson.toJson(file.name) + ")"
        browser.cefBrowser.executeJavaScript(js, browser.cefBrowser.url, 0)
    }

    private fun handle(msg: String) {
        val m = runCatching { JsonParser.parseString(msg).asJsonObject }.getOrNull() ?: return
        when (m.get("type")?.asString) {
            "ready" -> send()
            "rendered" -> {
                nodes = m.get("nodes")?.asInt ?: 0
                error = m.get("error")?.asString ?: ""
            }
            "builder" -> BrowserUtil.browse(Goome.BUILDER_URL)
            "copy" -> {
                val lang = PreviewPage.CODE_LANGS[m.get("lang")?.asString] ?: "the"
                CopyPasteManager.getInstance().setContents(StringSelection(m.get("code")?.asString ?: ""))
                val warnings = m.getAsJsonArray("warnings")?.map { it.asString } ?: emptyList()
                val extra = if (warnings.isEmpty()) "" else ", ${warnings.size} warning${if (warnings.size > 1) "s" else ""}: ${warnings[0]}"
                Goome.say(project, "copied the $lang code$extra")
            }
            "error" -> Goome.say(project, m.get("error")?.asString ?: "something broke in the preview", NotificationType.WARNING)
        }
    }

    override fun getComponent(): JComponent = browser.component
    override fun getPreferredFocusedComponent(): JComponent = browser.component
    override fun getName() = "Preview"
    override fun getFile() = file
    override fun setState(state: FileEditorState) {}
    override fun isModified() = false
    override fun isValid() = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun dispose() {}
}

class GoomePreviewProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile) = Goome.isProject(file) && JBCefApp.isSupported()

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val text = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
        return TextEditorWithPreview(text, GoomePreview(project, file), "goome", TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW)
    }

    override fun getEditorTypeId() = "goome-preview"
    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

class OpenBuilderAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun actionPerformed(e: AnActionEvent) = BrowserUtil.browse(Goome.BUILDER_URL)
}
