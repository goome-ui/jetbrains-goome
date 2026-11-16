package io.github.goomeui.goome

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SnippetTest {
    private val root = File(System.getProperty("goome.root") ?: ".")

    data class Snip(val prefix: String, val description: String, val text: String)

    private fun sibling(vararg names: String): File? =
        names.flatMap { listOf(File(root.parentFile, it), File(root, "deps/$it")) }.firstOrNull { it.exists() }

    private fun snippets(lang: String): List<Snip> {
        val obj = JsonParser.parseString(File(root, "snippets/$lang.json").readText()).asJsonObject
        return obj.entrySet().map { (_, v) ->
            val o = v.asJsonObject
            val body = o.get("body")
            val text = if (body.isJsonArray) body.asJsonArray.joinToString("\n") { it.asString } else body.asString
            Snip(o.get("prefix").asString, o.get("description").asString, expandSnippet(text))
        }
    }

    private fun expandSnippet(text: String): String {
        val defs = mutableMapOf<String, String>()
        Regex("\\$\\{(\\d+):([^}]*)}").findAll(text).forEach { defs.putIfAbsent(it.groupValues[1], it.groupValues[2]) }
        Regex("\\$\\{(\\d+)\\|([^|]*)\\|}").findAll(text).forEach { defs.putIfAbsent(it.groupValues[1], it.groupValues[2].split(",")[0]) }
        return text
            .replace(Regex("\\$\\{(\\d+)[:|][^}]*}")) { defs[it.groupValues[1]] ?: "" }
            .replace(Regex("\\$\\{(\\d+)}|\\$(\\d+)")) { defs[it.groupValues[1].ifEmpty { it.groupValues[2] }] ?: "" }
    }

    private fun expandTemplate(value: String, vars: Map<String, String>): String =
        Regex("\\$\\$|\\$([A-Za-z0-9_]+)\\$").replace(value) { m ->
            if (m.value == "$$") "$" else if (m.groupValues[1] == "END") "" else vars[m.groupValues[1]] ?: ""
        }

    private fun exprDefault(expr: String): String {
        if (expr.isEmpty()) return ""
        val first = Regex("\"((?:\\\\.|[^\"\\\\])*)\"").find(expr)?.groupValues?.get(1) ?: return ""
        val s = first.replace("\\\"", "\"").replace("\\\\", "\\")
        return if (expr.startsWith("list(")) s.split(",")[0] else s
    }

    private fun xml(path: String): org.w3c.dom.Document {
        val stream = javaClass.getResourceAsStream(path)
        assertNotNull("$path should be in the plugin", stream)
        return stream!!.use { DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(it) }
    }

    private fun liveTemplates(lang: String): List<Snip> {
        val doc = xml("/liveTemplates/goome-$lang.xml")
        val list = doc.getElementsByTagName("template")
        return (0 until list.length).map { i ->
            val t = list.item(i) as Element
            val vars = mutableMapOf<String, String>()
            val vl = t.getElementsByTagName("variable")
            for (j in 0 until vl.length) {
                val v = vl.item(j) as Element
                vars[v.getAttribute("name")] = exprDefault(v.getAttribute("expression"))
            }
            Snip(t.getAttribute("name"), t.getAttribute("description"), expandTemplate(t.getAttribute("value"), vars))
        }
    }

    private fun dotSettings(): Map<String, List<Snip>> {
        val doc = xml("/dotnet/Extensions/io.github.goomeui.goome/settings/goome.DotSettings")
        val all = doc.documentElement.childNodes
        val keys = mutableMapOf<String, MutableMap<String, String>>()
        for (i in 0 until all.length) {
            val e = all.item(i) as? Element ?: continue
            val key = e.getAttributeNS("http://schemas.microsoft.com/winfx/2006/xaml", "Key")
            val m = Regex("^/Default/PatternsAndTemplates/LiveTemplates/Template/=([0-9A-F]{32})(/.*)$").find(key) ?: continue
            keys.getOrPut(m.groupValues[1]) { mutableMapOf() }[m.groupValues[2]] = e.textContent
        }
        val out = mutableMapOf<String, MutableList<Snip>>()
        for ((_, k) in keys) {
            val vars = mutableMapOf<String, String>()
            for ((path, value) in k) {
                val f = Regex("^/Field/=([^/]+)/Expression/@EntryValue$").find(path) ?: continue
                vars[f.groupValues[1]] = exprDefault(value)
            }
            val scope = k.entries.first { it.key.endsWith("/Type/@EntryValue") }.value
            out.getOrPut(scope) { mutableListOf() } += Snip(k.getValue("/Shortcut/@EntryValue"), k.getValue("/Description/@EntryValue"), expandTemplate(k.getValue("/Text/@EntryValue"), vars))
        }
        return out
    }

    private fun same(want: List<Snip>, got: List<Snip>, tabsToSpaces: Boolean = false) {
        assertEquals(want.map { it.prefix }.sorted(), got.map { it.prefix }.sorted())
        val byPrefix = got.associateBy { it.prefix }
        for (s in want) {
            val g = byPrefix.getValue(s.prefix)
            assertEquals(s.prefix, s.description, g.description)
            val text = if (tabsToSpaces) s.text.replace(Regex("(?m)^\t+")) { "    ".repeat(it.value.length) } else s.text
            assertEquals(s.prefix, text, g.text)
        }
    }

    @Test
    fun cTemplatesMatchSnippets() = same(snippets("c"), liveTemplates("c"))

    @Test
    fun javaTemplatesMatchSnippets() = same(snippets("java"), liveTemplates("java"))

    @Test
    fun pythonTemplatesMatchSnippets() = same(snippets("python"), liveTemplates("python"), tabsToSpaces = true)

    @Test
    fun riderTemplatesMatchSnippets() {
        val ds = dotSettings()
        same(snippets("csharp"), ds.getValue("InCSharpFile"))
        same(snippets("c"), ds.getValue("InCppFile"))
    }

    @Test
    fun everyPrefixStartsWithGoome() {
        for (lang in listOf("c", "csharp", "python", "java")) for (s in snippets(lang)) assertTrue(s.prefix, s.prefix.startsWith("goome-"))
    }

    @Test
    fun sameAsVscodeGoome() {
        val vs = sibling("vscode-goome")
        assumeTrue("vscode-goome isnt next to this repo", vs != null)
        for (f in File(root, "snippets").listFiles()!! + File(root, "schemas").listFiles()!!) {
            val other = File(vs, f.parentFile.name + "/" + f.name)
            assertEquals(f.name + " drifted from vscode-goome", other.readText(), f.readText())
        }
    }

    @Test
    fun cNamesAreInGoomeH() {
        val c = sibling("c-goome")
        assumeTrue("c-goome isnt next to this repo", c != null)
        val header = File(c, "include/goome.h").readText()
        for (s in snippets("c")) for (m in Regex("\\b(goome_\\w+|GOOME_\\w+)\\b").findAll(s.text)) {
            assertTrue("${s.prefix} uses ${m.value} but goome.h doesnt have it", Regex("\\b" + m.value + "\\b").containsMatchIn(header))
        }
    }
}
