package io.github.goomeui.goome.sidebar

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.AppExecutorUtil
import io.github.goomeui.goome.Goome
import io.github.goomeui.goome.GoomeProjectFileType
import io.github.goomeui.goome.GoomeThemeFileType
import io.github.goomeui.goome.docs.SearchDocsAction
import io.github.goomeui.goome.newproject.NewProjectAction
import io.github.goomeui.goome.sidebar.GoomeSidebar.Item
import io.github.goomeui.goome.sidebar.GoomeSidebar.Kind
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class GoomeToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = GoomePanel(project, toolWindow.disposable)
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(panel.component, "", false))
        panel.refresh()
    }
}

class GoomePanel(private val project: Project, private val parent: Disposable) {
    private val rootNode = DefaultMutableTreeNode()
    private val model = DefaultTreeModel(rootNode)
    val tree = Tree(model)
    val component = SimpleToolWindowPanel(true, true)
    var files: List<GoomeSidebar.FileRef> = emptyList()
        private set
    var lang = "c"
        private set

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = Renderer()
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button != MouseEvent.BUTTON1 || e.clickCount != 1) return
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                itemOf(path)?.let { run(it) }
            }
        })
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode != KeyEvent.VK_ENTER) return
                tree.selectionPath?.let { p -> itemOf(p)?.let { run(it) } }
            }
        })
        component.setContent(ScrollPaneFactory.createScrollPane(tree, true))

        val bus = project.messageBus.connect(parent)
        bus.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { GoomeSidebar.isBuilderFile(it.path) }) refresh()
            }
        })
        bus.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                followFile(event.newFile)
            }
        })
        followFile(FileEditorManager.getInstance(project).selectedFiles.firstOrNull())
        rebuild()
    }

    private fun followFile(file: VirtualFile?) {
        if (file == null) return
        val l = GoomeSidebar.snippetLang(file.extension)
        if (l == lang) return
        lang = l
        rebuild()
    }

    fun findFiles(): List<GoomeSidebar.FileRef> {
        val scope = GlobalSearchScope.projectScope(project)
        return (FileTypeIndex.getFiles(GoomeProjectFileType.INSTANCE, scope) + FileTypeIndex.getFiles(GoomeThemeFileType.INSTANCE, scope))
            .map { GoomeSidebar.FileRef(it.url, it.path) }
    }

    fun refresh() {
        ReadAction.nonBlocking<List<GoomeSidebar.FileRef>> { findFiles() }
            .inSmartMode(project)
            .expireWith(parent)
            .finishOnUiThread(ModalityState.any()) { setFiles(it) }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    fun setFiles(refs: List<GoomeSidebar.FileRef>) {
        files = refs
        rebuild()
    }

    fun items(): List<Item> = GoomeSidebar.root(files, project.basePath, lang)

    private fun rebuild() {
        val closed = (0 until tree.rowCount).mapNotNull { r -> tree.getPathForRow(r) }
            .filter { tree.isCollapsed(it) }.mapNotNull { itemOf(it)?.id }.toSet()
        val opened = (0 until tree.rowCount).mapNotNull { r -> tree.getPathForRow(r) }
            .filter { tree.isExpanded(it) }.mapNotNull { itemOf(it)?.id }.toSet()
        rootNode.removeAllChildren()
        fun add(to: DefaultMutableTreeNode, item: Item) {
            val node = DefaultMutableTreeNode(item)
            to.add(node)
            item.children.forEach { add(node, it) }
        }
        items().forEach { add(rootNode, it) }
        model.reload()
        fun expand(node: DefaultMutableTreeNode) {
            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i) as DefaultMutableTreeNode
                val item = child.userObject as Item
                if (child.childCount == 0) continue
                val want = when {
                    item.id in opened -> true
                    item.id in closed -> false
                    else -> item.open
                }
                if (want) {
                    tree.expandPath(TreePath(child.path))
                    expand(child)
                }
            }
        }
        expand(rootNode)
    }

    private fun itemOf(path: TreePath): Item? = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? Item

    fun run(item: Item) {
        when (item.kind) {
            Kind.ACTION, Kind.EMPTY -> when (item.arg) {
                GoomeSidebar.NEW_PROJECT -> NewProjectAction.start(project)
                GoomeSidebar.BUILDER -> BrowserUtil.browse(Goome.BUILDER_URL)
                GoomeSidebar.SEARCH_DOCS -> SearchDocsAction.show(FileEditorManager.getInstance(project).selectedTextEditor, component)
                GoomeSidebar.DOCS -> BrowserUtil.browse(Goome.DOCS_URL)
            }
            Kind.FILE -> VirtualFileManager.getInstance().findFileByUrl(item.arg)?.let { FileEditorManager.getInstance(project).openFile(it, true) }
            Kind.TEMPLATE -> NewProjectAction.start(project, item.arg, item.arg2)
            Kind.SNIPPET -> insertSnippet(item.arg, item.arg2)
            else -> {}
        }
    }

    fun insertSnippet(lang: String, prefix: String): Boolean {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor == null) {
            Goome.say(project, "open a file first, then pick the snippet", NotificationType.WARNING)
            return false
        }
        val live = GoomeSidebar.LIVE_GROUPS[lang]?.let { TemplateSettings.getInstance().getTemplate(prefix, it) }
        IdeFocusManager.getInstance(project).requestFocus(editor.contentComponent, true)
        if (live != null) {
            CommandProcessor.getInstance().executeCommand(project, { TemplateManager.getInstance(project).startTemplate(editor, live) }, "Insert $prefix", null)
            return true
        }
        val snip = GoomeSidebar.readSnippets(lang).firstOrNull { it.prefix == prefix } ?: return false
        val text = GoomeSidebar.expand(snip.body)
        WriteCommandAction.runWriteCommandAction(project, "Insert $prefix", null, {
            val at = editor.caretModel.offset
            editor.document.insertString(at, text)
            editor.caretModel.moveToOffset(at + text.length)
        })
        return true
    }

    private class Renderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
            val item = (value as? DefaultMutableTreeNode)?.userObject as? Item ?: return
            icon = when (item.kind) {
                Kind.GROUP -> when (item.id) {
                    "quick" -> AllIcons.Actions.Lightning
                    "files" -> AllIcons.Nodes.Folder
                    "templates" -> AllIcons.Nodes.Template
                    else -> AllIcons.Nodes.Favorite
                }
                Kind.ACTION -> when (item.arg) {
                    GoomeSidebar.NEW_PROJECT -> AllIcons.General.Add
                    GoomeSidebar.SEARCH_DOCS -> AllIcons.Actions.Search
                    else -> AllIcons.General.Web
                }
                Kind.FILE -> Goome.ICON
                Kind.LANG -> AllIcons.Nodes.Module
                Kind.TEMPLATE -> AllIcons.Nodes.Template
                Kind.SNIPPET -> AllIcons.Nodes.Favorite
                Kind.EMPTY -> AllIcons.General.Information
            }
            append(item.label, if (item.kind == Kind.GROUP) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES)
            if (item.detail.isNotEmpty()) append("  " + item.detail, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            toolTipText = item.detail.ifEmpty { null }
        }
    }
}
