package io.github.goomeui.goome

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.goomeui.goome.newproject.GoomeCli
import java.io.File
import java.nio.file.Files

class CliTest : BasePlatformTestCase() {
    private val root = File(System.getProperty("goome.root") ?: ".")

    fun testNames() {
        assertNull(GoomeCli.validName("my-goome-app"))
        assertNotNull(GoomeCli.validName("---"))
        assertNotNull(GoomeCli.validName("a/b"))
        assertNotNull(GoomeCli.validName("a:b"))
    }

    fun testArgs() {
        assertEquals(listOf("new", "hud1", "--lang", "csharp", "--template", "hud", "--dir", "/x/hud1"), GoomeCli.newArgs("hud1", "csharp", "hud", "/x/hud1"))
        assertEquals(listOf("c", "csharp", "python", "java", "godot"), GoomeCli.LANGS.map { it.id })
        assertEquals(listOf("app", "overlay", "mod-menu", "hud"), GoomeCli.TEMPLATES.map { it.id })
    }

    fun testFreshScriptIsReused() {
        val dir = Files.createTempDirectory(File(root, "build").toPath().also { Files.createDirectories(it) }, "cli")
        val f = dir.resolve("goome.py")
        Files.writeString(f, "#!/usr/bin/env python3\nimport sys\n")
        assertEquals(f, GoomeCli.cliScript(dir, "https://invalid.invalid/goome.py"))
        assertTrue(GoomeCli.looksLikeCli(f))
        Files.writeString(f, "<html>not found</html>")
        assertFalse(GoomeCli.looksLikeCli(f))
    }

    fun testWhich() {
        val dir = Files.createTempDirectory(File(root, "build").toPath().also { Files.createDirectories(it) }, "path").toFile()
        val exe = File(dir, if (com.intellij.openapi.util.SystemInfo.isWindows) "goome.EXE" else "goome")
        exe.writeText("#!/bin/sh\necho goome 0.4.0\n")
        exe.setExecutable(true)
        assertEquals(exe.path, GoomeCli.whichOnPath("goome", dir.path))
        assertNull(GoomeCli.whichOnPath("goome-not-here", dir.path))
    }

    fun testRealCli() {
        val cli = listOf(File(root.parentFile, "goome-cli/goome.py"), File(root, "deps/goome-cli/goome.py")).firstOrNull { it.exists() }
        val py = GoomeCli.findPython("")
        if (cli == null || py == null) return
        val out = GoomeCli.run(GoomeCli.Runner(py.cmd, py.pre + cli.path, cli.path), listOf("--version"), root)
        assertTrue(out, out.contains("goome"))
    }
}
