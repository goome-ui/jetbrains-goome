package io.github.goomeui.goome.docs

import com.google.gson.JsonParser
import com.intellij.ide.BrowserUtil
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.intellij.ui.SimpleListCellRenderer
import io.github.goomeui.goome.Goome
import javax.swing.JComponent

object GoomeDocs {
    data class Fn(val name: String, val sig: String, val note: String, val url: String)

    val functions: Map<String, Fn> by lazy { load() }

    private fun load(): Map<String, Fn> {
        val stream = GoomeDocs::class.java.getResourceAsStream("/goome/hover.json") ?: return emptyMap()
        val root = stream.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        val out = linkedMapOf<String, Fn>()
        for ((name, v) in root.getAsJsonObject("functions").entrySet()) {
            val o = v.asJsonObject
            out[name] = Fn(name, o.get("sig").asString, o.get("note").asString, o.get("url").asString)
        }
        return out
    }

    private fun isWord(c: Char) = c == '_' || c.isLetterOrDigit()

    fun wordAt(text: CharSequence, offset: Int): String? {
        if (text.isEmpty()) return null
        var at = offset.coerceIn(0, text.length)
        if (at == text.length || !isWord(text[at])) at--
        if (at < 0 || !isWord(text[at])) return null
        var s = at
        var e = at + 1
        while (s > 0 && isWord(text[s - 1])) s--
        while (e < text.length && isWord(text[e])) e++
        val word = text.substring(s, e)
        return if (word.startsWith("goome_")) word else null
    }

    fun html(fn: Fn): String {
        val sb = StringBuilder()
        sb.append(DocumentationMarkup.DEFINITION_START).append("<code>").append(StringUtil.escapeXmlEntities(fn.sig)).append("</code>").append(DocumentationMarkup.DEFINITION_END)
        sb.append(DocumentationMarkup.CONTENT_START)
        if (fn.note.isNotEmpty()) sb.append("<p>").append(StringUtil.escapeXmlEntities(fn.note)).append("</p>")
        sb.append("<p><a href=\"").append(fn.url).append("\">goome docs</a></p>")
        sb.append(DocumentationMarkup.CONTENT_END)
        return sb.toString()
    }
}

class GoomeDocTargets : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val name = GoomeDocs.wordAt(file.viewProvider.contents, offset) ?: return emptyList()
        val fn = GoomeDocs.functions[name] ?: return emptyList()
        return listOf(GoomeDocTarget(fn))
    }
}

class GoomeDocTarget(val fn: GoomeDocs.Fn) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)
    override fun computePresentation() = TargetPresentation.builder(fn.name).icon(Goome.ICON).presentation()
    override fun computeDocumentationHint() = fn.sig
    override fun computeDocumentation() = DocumentationResult.documentation(GoomeDocs.html(fn)).externalUrl(fn.url)
}

class SearchDocsAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) = show(e.getData(CommonDataKeys.EDITOR), null)

    companion object {
        fun show(editor: Editor?, near: JComponent?) {
            val word = editor?.let { ed ->
                val sel = ed.selectionModel.selectedText?.trim()
                if (!sel.isNullOrEmpty() && sel.startsWith("goome_")) sel else GoomeDocs.wordAt(ed.document.charsSequence, ed.caretModel.offset)
            }
            val all = listOf(GoomeDocs.Fn("all the docs", "", "", Goome.DOCS_URL)) + GoomeDocs.functions.values
            val pick = all.firstOrNull { it.name == word }
            val builder = JBPopupFactory.getInstance().createPopupChooserBuilder(all)
                .setTitle("goome Docs")
                .setRenderer(SimpleListCellRenderer.create { label, fn, _ ->
                    label.text = if (fn.note.isEmpty()) fn.name else fn.name + "  " + fn.note
                    label.icon = Goome.ICON
                })
                .setNamerForFiltering { it.name }
                .setItemChosenCallback { BrowserUtil.browse(it.url) }
            if (pick != null) builder.setSelectedValue(pick, true)
            val popup = builder.createPopup()
            when {
                near != null -> popup.showInCenterOf(near)
                editor != null -> popup.showInBestPositionFor(editor)
                else -> popup.showInFocusCenter()
            }
        }
    }
}
