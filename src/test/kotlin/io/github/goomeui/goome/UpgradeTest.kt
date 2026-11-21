package io.github.goomeui.goome

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.goomeui.goome.newproject.GoomeCli
import io.github.goomeui.goome.web.Upgrade
import io.github.goomeui.goome.web.UpgradeAction
import io.github.goomeui.goome.web.WebBuild
import java.io.File

class UpgradeTest : BasePlatformTestCase() {
    private val runner = GoomeCli.Runner("python3", listOf("goome.py"), "goome.py")

    override fun tearDown() {
        try {
            WebBuild.exec = WebBuild.realExec
        } finally {
            super.tearDown()
        }
    }

    private fun json(update: Boolean = true, latest: String = "0.5.7", extra: String = "") =
        """{"ok":true,"lang":"csharp","root":"/w","current":"0.5.5","latest":"$latest","update":$update,"files":["MyApp.csproj"],"next":"dotnet restore"$extra}"""

    fun testArgs() {
        assertEquals(listOf("upgrade", "--check", "--json", "--dir", "/w"), Upgrade.args("/w", true))
        assertEquals(listOf("upgrade", "--json", "--dir", "/w"), Upgrade.args("/w", false))
    }

    fun testUpdateAvailable() {
        val r = Upgrade.interpret(10, json(), "", true)
        assertEquals(Upgrade.Outcome.Available("goome 0.5.5 -> 0.5.7 (csharp)", "0.5.5", "0.5.7", "csharp"), r)
    }

    fun testUpToDate() {
        assertEquals(Upgrade.Outcome.UpToDate("goome is up to date (0.5.5)"), Upgrade.interpret(0, json(false, "0.5.5"), "", true))
    }

    fun testApplied() {
        val r = Upgrade.interpret(0, json(extra = ""","applied":true"""), "", false)
        assertEquals(Upgrade.Outcome.Applied("updated goome to 0.5.7 (csharp)", "dotnet restore"), r)
    }

    fun testNotProject() {
        val r = Upgrade.interpret(2, """{"ok":false,"error":"no pin"}""", "", true)
        assertEquals(Upgrade.Outcome.NotProject("this doesnt look like a goome project"), r)
    }

    fun testOldCli() {
        val r = Upgrade.interpret(2, "", "goome: error: argument command: invalid choice: 'upgrade' (choose from 'new', 'build')", true)
        assertEquals(Upgrade.Outcome.OldCli("update the goome cli first"), r)
    }

    fun testErrorExitCodes() {
        for (code in listOf(3, 4)) {
            assertEquals(Upgrade.Outcome.Failed("zig is missing"), Upgrade.interpret(code, """{"ok":false,"error":"zig is missing"}""", "", false))
        }
        assertEquals(Upgrade.Outcome.Failed("goome: registry down"), Upgrade.interpret(4, "", "goome: registry down\n", true))
    }

    fun testMalformedJson() {
        assertTrue(Upgrade.interpret(0, "{nope", "", true) is Upgrade.Outcome.Failed)
        assertTrue(Upgrade.interpret(10, "", "", true) is Upgrade.Outcome.Failed)
        assertTrue(Upgrade.interpret(0, "[]", "", true) is Upgrade.Outcome.Failed)
    }

    fun testRunUsesTheExec() {
        var seen: List<String> = emptyList()
        WebBuild.exec = WebBuild.Exec { cmd, _ ->
            seen = cmd.getCommandLineList(null)
            WebBuild.Out(10, json(), "")
        }
        val r = Upgrade.run(runner, File("."), true)
        assertTrue(r is Upgrade.Outcome.Available)
        assertEquals(listOf("python3", "goome.py", "upgrade", "--check", "--json", "--dir"), seen.take(6))
    }

    fun testActionIsRegistered() {
        val am = ActionManager.getInstance()
        assertTrue(am.getAction("goome.UpdateGoome") is UpgradeAction)
        val tools = am.getAction("goome.Tools") as com.intellij.openapi.actionSystem.DefaultActionGroup
        assertTrue(tools.getChildren(null).any { it === am.getAction("goome.UpdateGoome") })
    }
}
