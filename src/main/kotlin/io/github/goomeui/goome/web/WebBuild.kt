package io.github.goomeui.goome.web

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager
import io.github.goomeui.goome.newproject.GoomeCli
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object WebBuild {
    const val TOOL_WINDOW = "goome build"

    data class Out(val exit: Int, val stdout: String, val stderr: String, val cancelled: Boolean = false, val timeout: Boolean = false)

    sealed class Result {
        data class Ok(val folder: File, val text: String) : Result()
        data class Failed(val text: String) : Result()
        object Cancelled : Result()
    }

    fun interface Exec {
        fun run(cmd: GeneralCommandLine, indicator: ProgressIndicator?): Out
    }

    val realExec = Exec { cmd, indicator ->
        val h = CapturingProcessHandler(cmd.withCharset(Charsets.UTF_8))
        val o = if (indicator != null) h.runProcessWithProgressIndicator(indicator, 900000) else h.runProcess(900000)
        Out(o.exitCode, o.stdout, o.stderr, o.isCancelled, o.isTimeout)
    }

    var exec: Exec = realExec

    fun isC(name: String?) = name != null && (name.endsWith(".c") || name.endsWith(".h"))

    fun outDir(base: File) = File(base, "web")

    fun buildArgs(file: String?, out: String, goome: String): List<String> {
        val a = mutableListOf("build", "--web")
        if (!file.isNullOrBlank()) a += file
        a += listOf("--out", out)
        if (goome.isNotBlank()) a += listOf("--goome", goome.trim())
        return a
    }

    fun serveArgs(folder: String, port: Int) = listOf("serve", folder, "--port", port.toString())

    fun cleanError(stderr: String, stdout: String, exit: Int): String {
        val text = stderr.ifBlank { stdout }.trim()
        return text.ifEmpty { "goome build failed with exit code $exit" }
    }

    fun firstLine(text: String) = text.lines().firstOrNull { it.isNotBlank() }?.trim() ?: text

    fun build(r: GoomeCli.Runner, file: String?, base: File, goome: String, indicator: ProgressIndicator? = null): Result {
        val out = outDir(base)
        val cmd = GeneralCommandLine(listOf(r.cmd) + r.pre + buildArgs(file, out.path, goome)).withWorkDirectory(base)
        val o = try {
            exec.run(cmd, indicator)
        } catch (e: Exception) {
            return Result.Failed(e.message ?: e.toString())
        }
        if (o.cancelled) return Result.Cancelled
        if (o.timeout) return Result.Failed("goome build took too long")
        if (o.exit != 0) return Result.Failed(cleanError(o.stderr, o.stdout, o.exit))
        return Result.Ok(out, o.stdout.trim())
    }

    fun freePort(): Int = ServerSocket(0).use { it.localPort }
}

@Service(Service.Level.PROJECT)
class WebLog(private val project: Project) : Disposable {
    val console: ConsoleView by lazy {
        TextConsoleBuilderFactory.getInstance().createBuilder(project).console.also { Disposer.register(this, it) }
    }

    fun print(text: String, error: Boolean = false) {
        console.print(text.trimEnd() + "\n", if (error) ConsoleViewContentType.ERROR_OUTPUT else ConsoleViewContentType.NORMAL_OUTPUT)
    }

    fun clear() = console.clear()

    fun show() {
        ApplicationManager.getApplication().invokeLater({
            ToolWindowManager.getInstance(project).getToolWindow(WebBuild.TOOL_WINDOW)?.activate(null)
        }, project.disposed)
    }

    override fun dispose() {}

    companion object {
        fun get(project: Project): WebLog = project.service()
    }
}

@Service(Service.Level.PROJECT)
class WebServer : Disposable {
    var launcher: (GeneralCommandLine) -> ProcessHandler = { KillableProcessHandler(it.withCharset(Charsets.UTF_8)) }

    @Volatile
    private var handler: ProcessHandler? = null

    @Volatile
    var folder: String? = null
        private set

    @Volatile
    var url: String? = null
        private set

    fun running() = handler?.let { !it.isProcessTerminated && !it.isProcessTerminating } == true

    fun start(r: GoomeCli.Runner, dir: File, port: Int = WebBuild.freePort(), waitSeconds: Long = 15): String {
        if (running() && folder == dir.path) return url!!
        stop()
        val cmd = GeneralCommandLine(listOf(r.cmd) + r.pre + WebBuild.serveArgs(dir.path, port)).withWorkDirectory(dir)
        val h = launcher(cmd)
        val up = CountDownLatch(1)
        val seen = StringBuilder()
        var found: String? = null
        h.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                seen.append(event.text)
                val m = Regex("https?://\\S+?(?=[\\s)]|$)").find(event.text)
                if (found == null && event.text.contains("serving") && m != null) {
                    found = m.value
                    up.countDown()
                }
            }

            override fun processTerminated(event: ProcessEvent) {
                up.countDown()
            }
        })
        h.startNotify()
        up.await(waitSeconds, TimeUnit.SECONDS)
        val u = found
        if (u == null) {
            val text = seen.toString().trim()
            h.destroyProcess()
            throw IllegalStateException(text.ifEmpty { "the web server didnt start" })
        }
        handler = h
        folder = dir.path
        url = u
        return u
    }

    fun stop(): Boolean {
        val h = handler ?: return false
        handler = null
        folder = null
        url = null
        if (!h.isProcessTerminated) h.destroyProcess()
        return true
    }

    override fun dispose() {
        stop()
    }

    companion object {
        fun get(project: Project): WebServer = project.service()
    }
}
