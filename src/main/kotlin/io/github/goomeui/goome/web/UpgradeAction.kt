package io.github.goomeui.goome.web

import com.intellij.execution.process.KillableProcessHandler
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import io.github.goomeui.goome.newproject.GoomeCli
import java.io.File

class UpgradeAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        start(project)
    }

    companion object {
        fun projectDir(project: Project): File? {
            project.basePath?.let { return File(it) }
            val open = FileEditorManager.getInstance(project).selectedFiles.firstOrNull { !it.isDirectory }
            return open?.parent?.path?.let { File(it) }
        }

        fun start(project: Project) {
            val dir = projectDir(project)
            if (dir == null) {
                WebNotes.note(project, "open your project folder first", NotificationType.WARNING)
                return
            }
            FileDocumentManager.getInstance().saveAllDocuments()
            checkNow(project, dir)
        }

        private fun checkNow(project: Project, dir: File) {
            val log = WebLog.get(project)
            object : Task.Backgroundable(project, "Checking for a newer goome", true) {
                var outcome: Upgrade.Outcome = Upgrade.Outcome.Failed("cancelled")

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    val r = GoomeCli.findRunner()
                    log.clear()
                    log.print("goome ${Upgrade.args(dir.path, true).joinToString(" ")}")
                    outcome = Upgrade.run(r, dir, true, indicator)
                }

                override fun onSuccess() = show(project, dir, outcome)

                override fun onThrowable(error: Throwable) {
                    show(project, dir, Upgrade.Outcome.Failed(error.message ?: error.toString()))
                }
            }.queue()
        }

        private fun applyUpdate(project: Project, dir: File) {
            val log = WebLog.get(project)
            object : Task.Backgroundable(project, "Updating goome", true) {
                var outcome: Upgrade.Outcome = Upgrade.Outcome.Failed("cancelled")

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    val r = GoomeCli.findRunner()
                    log.print("goome ${Upgrade.args(dir.path, false).joinToString(" ")}")
                    outcome = Upgrade.run(r, dir, false, indicator)
                }

                override fun onSuccess() = show(project, dir, outcome)

                override fun onThrowable(error: Throwable) {
                    show(project, dir, Upgrade.Outcome.Failed(error.message ?: error.toString()))
                }
            }.queue()
        }

        private fun updateCli(project: Project, dir: File) {
            val log = WebLog.get(project)
            object : Task.Backgroundable(project, "Updating the goome cli", true) {
                var cliError: String? = null

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    val r = GoomeCli.findRunner()
                    log.print("goome update")
                    cliError = Upgrade.updateCli(r, dir, indicator)
                }

                override fun onSuccess() {
                    val why = cliError
                    if (why == null) checkNow(project, dir) else WebNotes.failed(project, why)
                }

                override fun onThrowable(error: Throwable) {
                    WebNotes.failed(project, error.message ?: error.toString())
                }
            }.queue()
        }

        fun runNext(project: Project, dir: File, next: String) {
            val log = WebLog.get(project)
            log.print("$ $next")
            log.show()
            val handler = KillableProcessHandler(Upgrade.shell(next, dir))
            log.console.attachToProcess(handler)
            handler.startNotify()
        }

        fun show(project: Project, dir: File, outcome: Upgrade.Outcome) {
            val log = WebLog.get(project)
            when (outcome) {
                is Upgrade.Outcome.Available -> {
                    log.print(outcome.message)
                    WebNotes.note(project, outcome.message, NotificationType.INFORMATION, NotificationAction.createSimpleExpiring("Update") { applyUpdate(project, dir) })
                }
                is Upgrade.Outcome.UpToDate -> {
                    log.print(outcome.message)
                    WebNotes.note(project, outcome.message, NotificationType.INFORMATION)
                }
                is Upgrade.Outcome.Applied -> {
                    log.print(outcome.message)
                    val next = outcome.next
                    if (next == null) WebNotes.note(project, outcome.message, NotificationType.INFORMATION)
                    else WebNotes.note(project, outcome.message, NotificationType.INFORMATION, NotificationAction.createSimpleExpiring("Run $next") { runNext(project, dir, next) })
                }
                is Upgrade.Outcome.NotProject -> {
                    log.print(outcome.message, true)
                    WebNotes.note(project, outcome.message, NotificationType.WARNING)
                }
                is Upgrade.Outcome.OldCli -> {
                    log.print(outcome.message, true)
                    WebNotes.note(project, outcome.message, NotificationType.WARNING, NotificationAction.createSimpleExpiring("Update CLI") { updateCli(project, dir) })
                }
                is Upgrade.Outcome.Failed -> WebNotes.failed(project, outcome.message)
            }
        }
    }
}
