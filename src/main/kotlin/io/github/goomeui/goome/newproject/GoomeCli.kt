package io.github.goomeui.goome.newproject

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.HttpRequests
import io.github.goomeui.goome.GoomeSettings
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object GoomeCli {
    const val CLI_URL = "https://github.com/goome-ui/goome-cli/releases/latest/download/goome.py"

    data class Choice(val id: String, val label: String, val detail: String) {
        override fun toString() = "$label - $detail"
    }

    val LANGS = listOf(
        Choice("c", "C", "one goome.h, builds with any C compiler"),
        Choice("csharp", "C#", ".NET 8 or newer"),
        Choice("python", "Python", "Python 3.8 or newer"),
        Choice("java", "Java", "Java 22 or newer, Gradle"),
        Choice("godot", "Godot", "Godot 4.4 or newer, GDScript"),
        Choice("rust", "Rust", "Rust 1.77 or newer, cargo"),
        Choice("node", "Node", "Node 18 or newer, npm"),
        Choice("go", "Go", "Go 1.23 or newer, cgo with a C compiler"),
        Choice("zig", "Zig", "Zig 0.15.2"),
        Choice("lua", "Lua", "Lua 5.4 or LuaJIT, LuaRocks and a C compiler"),
    )

    val TEMPLATES = listOf(
        Choice("app", "app", "a normal window, with a toggle that turns it into an overlay"),
        Choice("overlay", "overlay", "a see-through overlay, only the window takes clicks"),
        Choice("mod-menu", "mod-menu", "a menu over the game that a hotkey opens and closes"),
        Choice("hud", "hud", "a little info panel in the corner, clicks go through it"),
    )

    data class Runner(val cmd: String, val pre: List<String>, val how: String)

    fun validName(name: String): String? {
        if (!Regex("[A-Za-z0-9]").containsMatchIn(name)) return "use letters and numbers"
        if (Regex("[\\\\/:*?\"<>|]").containsMatchIn(name)) return "no slashes or other path stuff in the name"
        return null
    }

    fun whichOnPath(cmd: String, path: String? = System.getenv("PATH")): String? {
        val exts = if (SystemInfo.isWindows) (System.getenv("PATHEXT") ?: ".EXE;.CMD;.BAT").split(";").filter { it.isNotEmpty() } else listOf("")
        for (dir in (path ?: "").split(File.pathSeparator)) {
            if (dir.isEmpty()) continue
            for (ext in exts) {
                val f = File(dir, cmd + ext)
                if (f.isFile && (SystemInfo.isWindows || f.canExecute())) return f.path
            }
        }
        return null
    }

    private fun works(cmd: String, args: List<String>): Boolean = try {
        ExecUtil.execAndGetOutput(GeneralCommandLine(listOf(cmd) + args), 15000).exitCode == 0
    } catch (e: Exception) {
        false
    }

    fun findPython(setting: String): Runner? {
        val tries = mutableListOf<Pair<String, List<String>>>()
        if (setting.isNotBlank()) tries += setting.trim() to emptyList()
        if (SystemInfo.isWindows) tries += listOf("py" to listOf("-3"), "python" to emptyList(), "python3" to emptyList())
        else tries += listOf("python3" to emptyList(), "python" to emptyList())
        for ((cmd, pre) in tries) {
            if (works(cmd, pre + listOf("-c", "import sys; sys.exit(0 if sys.version_info >= (3, 8) else 1)"))) return Runner(cmd, pre, cmd)
        }
        return null
    }

    fun storage(): Path = PathManager.getSystemDir().resolve("goome")

    fun looksLikeCli(file: Path): Boolean {
        val head = Files.newInputStream(file).use { String(it.readNBytes(200), Charsets.UTF_8) }
        return head.startsWith("#!") || head.contains("import")
    }

    fun cliScript(dir: Path = storage(), url: String = CLI_URL): Path {
        Files.createDirectories(dir)
        val file = dir.resolve("goome.py")
        val fresh = Files.exists(file) && System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() < 24 * 3600 * 1000L
        if (fresh) return file
        val part = dir.resolve("goome.py.part")
        try {
            HttpRequests.request(url).userAgent("jetbrains-goome").connectTimeout(30000).readTimeout(30000).saveToFile(part.toFile(), null)
            if (!looksLikeCli(part)) throw IllegalStateException("that download doesnt look like goome.py")
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            Files.deleteIfExists(part)
            if (!Files.exists(file)) throw e
        }
        return file
    }

    fun findRunner(): Runner {
        val s = GoomeSettings.get()
        val cli = s.cli.trim()
        if (cli.isNotEmpty()) {
            if (cli.endsWith(".py")) {
                val p = findPython(s.python) ?: throw IllegalStateException("the goome setting is a .py file but theres no Python 3.8+ here")
                return Runner(p.cmd, p.pre + cli, cli)
            }
            return Runner(cli, emptyList(), cli)
        }
        val onPath = whichOnPath("goome")
        if (onPath != null && works(onPath, listOf("--version"))) return Runner(onPath, emptyList(), onPath)
        val p = findPython(s.python) ?: throw IllegalStateException("goome needs Python 3.8 or newer to make projects, and i couldnt find it. install it or set it in Settings > Tools > goome")
        val script = cliScript().toString()
        return Runner(p.cmd, p.pre + script, script)
    }

    fun newArgs(name: String, lang: String, template: String, dest: String) = listOf("new", name, "--lang", lang, "--template", template, "--dir", dest)

    fun run(r: Runner, args: List<String>, cwd: File): String {
        val cmd = GeneralCommandLine(listOf(r.cmd) + r.pre + args).withWorkDirectory(cwd).withCharset(Charsets.UTF_8)
        val out = ExecUtil.execAndGetOutput(cmd, 180000)
        if (out.isTimeout) throw IllegalStateException("goome-cli took too long")
        if (out.exitCode != 0) {
            val text = (out.stderr.ifBlank { out.stdout }).trim()
            throw IllegalStateException(text.lines().lastOrNull { it.isNotBlank() } ?: "goome-cli failed with ${out.exitCode}")
        }
        return out.stdout
    }
}
