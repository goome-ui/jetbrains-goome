package io.github.goomeui.goome.web

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.progress.ProgressIndicator
import io.github.goomeui.goome.newproject.GoomeCli
import java.io.File

object Upgrade {
    sealed class Outcome {
        abstract val message: String

        data class Available(override val message: String, val current: String, val latest: String, val lang: String) : Outcome()
        data class UpToDate(override val message: String) : Outcome()
        data class Applied(override val message: String, val next: String?) : Outcome()
        data class NotProject(override val message: String) : Outcome()
        data class OldCli(override val message: String) : Outcome()
        data class Failed(override val message: String) : Outcome()
    }

    fun args(dir: String, check: Boolean): List<String> {
        val a = mutableListOf("upgrade")
        if (check) a += "--check"
        a += listOf("--json", "--dir", dir)
        return a
    }

    private fun parse(stdout: String): JsonObject? {
        val text = stdout.trim()
        if (text.isEmpty()) return null
        val lastBrace = text.lines().lastOrNull { it.trim().startsWith("{") }
        for (t in listOfNotNull(text, lastBrace)) {
            val v = runCatching { JsonParser.parseString(t) }.getOrNull()
            if (v != null && v.isJsonObject) return v.asJsonObject
        }
        return null
    }

    private fun text(v: JsonElement?): String = if (v != null && v.isJsonPrimitive && v.asJsonPrimitive.isString) v.asString else ""

    fun interpret(exit: Int, stdout: String, stderr: String, check: Boolean): Outcome {
        val json = parse(stdout)
        if (json == null && stderr.contains("invalid choice", true)) return Outcome.OldCli("update the goome cli first")
        if (exit == 2) return Outcome.NotProject("this doesnt look like a goome project")
        if (exit == 3 || exit == 4) {
            val why = text(json?.get("error")).ifEmpty { stderr.trim().lines().lastOrNull { it.isNotBlank() } ?: "" }
            return Outcome.Failed(why.ifEmpty { "goome upgrade failed (exit $exit)" })
        }
        if (json == null) return Outcome.Failed("couldnt read what goome upgrade said (exit $exit)")
        val ok = json.get("ok")
        if (ok != null && ok.isJsonPrimitive && ok.asJsonPrimitive.isBoolean && !ok.asBoolean) {
            return Outcome.Failed(text(json.get("error")).ifEmpty { "goome upgrade failed" })
        }
        val current = text(json.get("current"))
        val latest = text(json.get("latest"))
        val lang = text(json.get("lang"))
        val update = json.get("update")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true
        val tail = if (lang.isEmpty()) "" else " ($lang)"
        if (check) {
            if (update || exit == 10) return Outcome.Available("goome $current -> $latest$tail", current, latest, lang)
            if (exit != 0) return Outcome.Failed("goome upgrade failed (exit $exit)")
            return Outcome.UpToDate("goome is up to date ($current)")
        }
        if (exit != 0) return Outcome.Failed(text(json.get("error")).ifEmpty { "goome upgrade failed (exit $exit)" })
        val applied = json.get("applied")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true
        if (applied) return Outcome.Applied("updated goome to $latest$tail", text(json.get("next")).ifEmpty { null })
        return Outcome.UpToDate("goome is up to date ($current)")
    }

    fun run(r: GoomeCli.Runner, dir: File, check: Boolean, indicator: ProgressIndicator? = null): Outcome {
        val cmd = GeneralCommandLine(listOf(r.cmd) + r.pre + args(dir.path, check)).withWorkDirectory(dir)
        val o = try {
            WebBuild.exec.run(cmd, indicator)
        } catch (e: Exception) {
            return Outcome.Failed(e.message ?: e.toString())
        }
        if (o.cancelled) return Outcome.Failed("cancelled")
        if (o.timeout) return Outcome.Failed("goome upgrade took too long")
        return interpret(o.exit, o.stdout, o.stderr, check)
    }

    fun updateCli(r: GoomeCli.Runner, dir: File, indicator: ProgressIndicator? = null): String? {
        val cmd = GeneralCommandLine(listOf(r.cmd) + r.pre + "update").withWorkDirectory(dir)
        val o = try {
            WebBuild.exec.run(cmd, indicator)
        } catch (e: Exception) {
            return e.message ?: e.toString()
        }
        if (o.exit == 0) return null
        val text = o.stderr.ifBlank { o.stdout }.trim()
        return text.lines().lastOrNull { it.isNotBlank() } ?: "goome update failed (exit ${o.exit})"
    }

    fun shell(next: String, dir: File): GeneralCommandLine {
        val parts = if (System.getProperty("os.name").lowercase().contains("win")) listOf("cmd", "/c", next) else listOf("sh", "-c", next)
        return GeneralCommandLine(parts).withWorkDirectory(dir)
    }
}
