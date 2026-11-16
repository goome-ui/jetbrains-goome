package io.github.goomeui.goome

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.goomeui.goome.newproject.GoomeCli
import io.github.goomeui.goome.web.StopWebServerAction
import io.github.goomeui.goome.web.WebBuild
import io.github.goomeui.goome.web.WebBuildAction
import io.github.goomeui.goome.web.WebLog
import io.github.goomeui.goome.web.WebServer
import java.io.File
import java.io.OutputStream
import java.nio.file.Files

class WebTest : BasePlatformTestCase() {
    private val root = File(System.getProperty("goome.root") ?: ".")
    private val runner = GoomeCli.Runner("python3", listOf("goome.py"), "goome.py")

    override fun tearDown() {
        try {
            WebBuild.exec = WebBuild.realExec
        } finally {
            super.tearDown()
        }
    }

    private fun scratch(): File = Files.createTempDirectory(File(root, "build").toPath().also { Files.createDirectories(it) }, "web").toFile()

    private class FakeHandler(private val line: String?, private val dieNow: Boolean = false) : ProcessHandler() {
        var destroyed = false

        override fun destroyProcessImpl() {
            destroyed = true
            notifyProcessTerminated(0)
        }

        override fun detachProcessImpl() = notifyProcessDetached()
        override fun detachIsDefault() = false
        override fun getProcessInput(): OutputStream? = null

        override fun startNotify() {
            super.startNotify()
            if (line != null) notifyTextAvailable(line, ProcessOutputTypes.STDOUT)
            if (dieNow) notifyProcessTerminated(1)
        }
    }

    fun testArgs() {
        assertEquals(listOf("build", "--web", "--out", "/p/web"), WebBuild.buildArgs(null, "/p/web", ""))
        assertEquals(listOf("build", "--web", "/p/main.c", "--out", "/p/web", "--goome", "/src/c-goome"), WebBuild.buildArgs("/p/main.c", "/p/web", " /src/c-goome "))
        assertEquals(listOf("serve", "/p/web", "--port", "8123"), WebBuild.serveArgs("/p/web", 8123))
        assertTrue(WebBuild.isC("main.c"))
        assertFalse(WebBuild.isC("main.cpp"))
        assertFalse(WebBuild.isC(null))
    }

    fun testBuildSuccess() {
        val base = scratch()
        var seen: GeneralCommandLine? = null
        WebBuild.exec = WebBuild.Exec { cmd, _ ->
            seen = cmd
            WebBuild.Out(0, "built web/\n", "")
        }
        val res = WebBuild.build(runner, File(base, "main.c").path, base, "/src/c-goome")
        assertTrue(res.toString(), res is WebBuild.Result.Ok)
        assertEquals(File(base, "web"), (res as WebBuild.Result.Ok).folder)
        assertEquals(listOf("python3", "goome.py", "build", "--web", File(base, "main.c").path, "--out", File(base, "web").path, "--goome", "/src/c-goome"), seen!!.commandLineString.let { listOf(seen!!.exePath) + seen!!.parametersList.list })
        assertEquals(base, seen!!.workDirectory)
    }

    fun testBuildErrorKeepsFullText() {
        val base = scratch()
        val err = "goome: no clang found, building for the web needs one.\ninstall clang and lld with the webassembly target:\n  sudo apt install clang lld\n"
        WebBuild.exec = WebBuild.Exec { _, _ -> WebBuild.Out(1, "", err) }
        val res = WebBuild.build(runner, null, base, "")
        assertTrue(res is WebBuild.Result.Failed)
        assertEquals(err.trim(), (res as WebBuild.Result.Failed).text)
        assertEquals("goome: no clang found, building for the web needs one.", WebBuild.firstLine(res.text))
    }

    fun testBuildEdgeCases() {
        val base = scratch()
        WebBuild.exec = WebBuild.Exec { _, _ -> WebBuild.Out(2, "", "") }
        assertEquals("goome build failed with exit code 2", (WebBuild.build(runner, null, base, "") as WebBuild.Result.Failed).text)
        WebBuild.exec = WebBuild.Exec { _, _ -> WebBuild.Out(-1, "", "", cancelled = true) }
        assertSame(WebBuild.Result.Cancelled, WebBuild.build(runner, null, base, ""))
        WebBuild.exec = WebBuild.Exec { _, _ -> WebBuild.Out(-1, "", "", timeout = true) }
        assertTrue(WebBuild.build(runner, null, base, "") is WebBuild.Result.Failed)
        WebBuild.exec = WebBuild.Exec { _, _ -> throw IllegalStateException("cant start python") }
        assertEquals("cant start python", (WebBuild.build(runner, null, base, "") as WebBuild.Result.Failed).text)
    }

    fun testServerStartsAndStops() {
        val server = WebServer()
        val h = FakeHandler("serving /p/web at http://localhost:8123/  (ctrl+c to stop)\n")
        var cmd: GeneralCommandLine? = null
        server.launcher = { cmd = it; h }
        val dir = scratch()
        val url = server.start(runner, dir, 8123, 5)
        assertEquals("http://localhost:8123/", url)
        assertTrue(server.running())
        assertEquals(listOf("goome.py", "serve", dir.path, "--port", "8123"), cmd!!.parametersList.list)
        assertEquals(url, server.start(runner, dir, 8123, 5))
        assertTrue(server.stop())
        assertTrue(h.destroyed)
        assertFalse(server.running())
        assertFalse(server.stop())
    }

    fun testServerFailureShowsOutput() {
        val server = WebServer()
        server.launcher = { FakeHandler("goome: cant listen on 127.0.0.1:8000 (Address already in use)\n", dieNow = true) }
        try {
            server.start(runner, scratch(), 8000, 5)
            fail("should have thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!, e.message!!.contains("Address already in use"))
        }
        assertFalse(server.running())
    }

    fun testServerStoppedOnDispose() {
        val server = WebServer()
        val h = FakeHandler("serving x at http://localhost:9000/\n")
        server.launcher = { h }
        server.start(runner, scratch(), 9000, 5)
        server.dispose()
        assertTrue(h.destroyed)
    }

    fun testActionsAreRegistered() {
        val am = ActionManager.getInstance()
        assertTrue(am.getAction("goome.BuildWeb") is WebBuildAction)
        assertTrue(am.getAction("goome.StopWebServer") is StopWebServerAction)
        val tools = am.getAction("goome.Tools") as com.intellij.openapi.actionSystem.DefaultActionGroup
        assertTrue(tools.getChildren(null).any { it === am.getAction("goome.BuildWeb") })
        assertTrue((am.getAction("EditorPopupMenu") as com.intellij.openapi.actionSystem.DefaultActionGroup).getChildren(am).any { am.getId(it) == "goome.BuildWeb" })
        assertTrue((am.getAction("ProjectViewPopupMenu") as com.intellij.openapi.actionSystem.DefaultActionGroup).getChildren(am).any { am.getId(it) == "goome.BuildWeb" })
        assertEquals("bottom", ToolWindowEP.EP_NAME.extensionList.single { it.id == WebBuild.TOOL_WINDOW }.anchor)
    }

    fun testContextMenuOnlyForCFiles() {
        val action = WebBuildAction()
        val c = myFixture.configureByText("main.c", "int main(void) { return 0; }").virtualFile
        val txt = myFixture.configureByText("notes.txt", "x").virtualFile
        fun visible(place: String, file: com.intellij.openapi.vfs.VirtualFile): Boolean {
            val ctx = com.intellij.openapi.actionSystem.impl.SimpleDataContext.builder()
                .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                .add(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE, file).build()
            val event = com.intellij.openapi.actionSystem.AnActionEvent.createFromDataContext(place, action.templatePresentation.clone(), ctx)
            action.update(event)
            return event.presentation.isEnabledAndVisible
        }
        assertTrue(visible(com.intellij.openapi.actionSystem.ActionPlaces.EDITOR_POPUP, c))
        assertFalse(visible(com.intellij.openapi.actionSystem.ActionPlaces.EDITOR_POPUP, txt))
        assertFalse(visible(com.intellij.openapi.actionSystem.ActionPlaces.PROJECT_VIEW_POPUP, txt))
        assertTrue(visible(com.intellij.openapi.actionSystem.ActionPlaces.MAIN_MENU, txt))
    }

    fun testStopActionFollowsServer() {
        val action = StopWebServerAction()
        val server = WebServer.get(project)
        server.launcher = { FakeHandler("serving x at http://localhost:9001/\n") }
        fun enabled(): Boolean {
            val ctx = com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext(project)
            val event = com.intellij.openapi.actionSystem.AnActionEvent.createFromDataContext("test", action.templatePresentation.clone(), ctx)
            action.update(event)
            return event.presentation.isEnabled
        }
        assertFalse(enabled())
        server.start(runner, scratch(), 9001, 5)
        assertTrue(enabled())
        server.stop()
        assertFalse(enabled())
    }

    fun testSettingAndLog() {
        val s = GoomeSettings.get()
        val before = s.goomeSrc
        try {
            assertEquals("", before)
            s.goomeSrc = "/src/c-goome"
            assertEquals("/src/c-goome", GoomeSettings.get().goomeSrc)
        } finally {
            s.goomeSrc = before
        }
        WebLog.get(project).print("hello", false)
        WebLog.get(project).print("bad", true)
    }
}
