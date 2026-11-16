package io.github.goomeui.goome.newproject

import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.github.goomeui.goome.Goome
import java.io.File
import javax.swing.JComponent

class NewProjectDialog(project: Project?, langId: String? = null, templateId: String? = null) : DialogWrapper(project, true) {
    val lang = ComboBox(GoomeCli.LANGS.toTypedArray())
    val template = ComboBox(GoomeCli.TEMPLATES.toTypedArray())
    val folder = TextFieldWithBrowseButton()
    val name = JBTextField("my-goome-app")

    init {
        title = "goome: New Project"
        folder.text = project?.basePath?.let { File(it).parent } ?: System.getProperty("user.home")
        folder.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Where Should the Project Go?"))
        GoomeCli.LANGS.firstOrNull { it.id == langId }?.let { lang.selectedItem = it }
        GoomeCli.TEMPLATES.firstOrNull { it.id == templateId }?.let { template.selectedItem = it }
        setOKButtonText("Make It")
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Language:") { cell(lang).align(AlignX.FILL) }
        row("Template:") { cell(template).align(AlignX.FILL) }
        row("Folder:") { cell(folder).align(AlignX.FILL) }
        row("Name:") { cell(name).align(AlignX.FILL) }
    }

    override fun getPreferredFocusedComponent() = name

    fun dest() = File(folder.text.trim(), name.text.trim())

    override fun doValidate(): ValidationInfo? {
        val n = name.text.trim()
        GoomeCli.validName(n)?.let { return ValidationInfo(it, name) }
        val dir = File(folder.text.trim())
        if (folder.text.isBlank() || !dir.isDirectory) return ValidationInfo("pick a folder that exists", folder)
        val d = dest()
        if (d.exists() && (!d.isDirectory || (d.list()?.isNotEmpty() == true))) return ValidationInfo("$n already exists in there and isnt empty", name)
        return null
    }
}

class NewProjectAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) = start(e.project)

    companion object {
        fun start(project: Project?, langId: String? = null, templateId: String? = null) {
            val dlg = NewProjectDialog(project, langId, templateId)
            if (dlg.showAndGet()) make(project, dlg)
        }

        private fun make(project: Project?, dlg: NewProjectDialog) {
            val lang = (dlg.lang.selectedItem as GoomeCli.Choice).id
            val template = (dlg.template.selectedItem as GoomeCli.Choice).id
            val name = dlg.name.text.trim()
            val parent = File(dlg.folder.text.trim())
            val dest = dlg.dest()
            object : Task.Modal(project, "Making $name...", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "finding goome-cli"
                    val r = GoomeCli.findRunner()
                    indicator.text = "running " + r.how
                    val out = GoomeCli.run(r, GoomeCli.newArgs(name, lang, template, dest.path), parent)
                    if (!dest.isDirectory) throw IllegalStateException("goome-cli finished but ${dest.path} isnt there: ${out.trim()}")
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dest)
                }

                override fun onSuccess() {
                    ProjectUtil.openOrImport(dest.toPath(), project, false)
                }

                override fun onThrowable(error: Throwable) {
                    Goome.say(project, error.message ?: error.toString(), NotificationType.ERROR)
                }
            }.queue()
        }
    }
}
