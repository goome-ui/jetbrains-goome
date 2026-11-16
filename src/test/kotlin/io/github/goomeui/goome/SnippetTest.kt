package io.github.goomeui.goome

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            .replace(Regex("\\\\([$}\\\\])")) { it.groupValues[1] }
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
    fun rustTemplatesMatchSnippets() = same(snippets("rust"), liveTemplates("rust"), tabsToSpaces = true)

    @Test
    fun nodeTemplatesMatchSnippets() = same(snippets("node"), liveTemplates("node"))

    @Test
    fun goTemplatesMatchSnippets() = same(snippets("go"), liveTemplates("go"))

    @Test
    fun zigTemplatesMatchSnippets() = same(snippets("zig"), liveTemplates("zig"), tabsToSpaces = true)

    @Test
    fun luaTemplatesMatchSnippets() = same(snippets("lua"), liveTemplates("lua"), tabsToSpaces = true)

    @Test
    fun godotTemplatesMatchSnippets() = same(snippets("godot"), liveTemplates("godot"))

    @Test
    fun templatesUseTheRightContexts() {
        val want = mapOf(
            "c" to setOf("OC_DECLARATION_C", "OC_STATEMENT_C", "OC_DECLARATION_CPP", "OC_STATEMENT_CPP"),
            "java" to setOf("JAVA_CODE"),
            "python" to setOf("Python"),
            "rust" to setOf("RUST_ITEM", "RUST_STATEMENT"),
            "node" to setOf("JS_TOP_LEVEL_STATEMENT", "JS_STATEMENT", "TS_TOP_LEVEL_STATEMENT", "TS_STATEMENT"),
            "go" to setOf("GO_FILE", "GO_STATEMENT"),
            "zig" to setOf("OTHER"),
            "lua" to setOf("LUA_CODE"),
            "godot" to setOf("GDSCRIPT"),
        )
        for ((lang, contexts) in want) {
            val list = xml("/liveTemplates/goome-$lang.xml").getElementsByTagName("option")
            val got = (0 until list.length).map { (list.item(it) as Element).getAttribute("name") }.toSet()
            assertEquals(lang, contexts, got)
        }
    }

    @Test
    fun optionalPluginsHaveTheirOwnConfig() {
        val doc = xml("/META-INF/plugin.xml")
        val list = doc.getElementsByTagName("depends")
        val optional = (0 until list.length).map { list.item(it) as Element }.filter { it.getAttribute("optional") == "true" }
        val byPlugin = optional.associate { it.textContent.trim() to it.getAttribute("config-file") }
        assertEquals(
            mapOf(
                "com.intellij.java" to "goome-java.xml", "com.intellij.modules.python" to "goome-python.xml", "com.intellij.modules.clion" to "goome-c.xml",
                "com.jetbrains.rust" to "goome-rust.xml", "JavaScript" to "goome-node.xml", "org.jetbrains.plugins.go" to "goome-go.xml",
                "com.falsepattern.zigbrains" to "goome-zig.xml", "com.tang" to "goome-lua.xml", "com.intellij.rider.godot" to "goome-godot.xml",
            ),
            byPlugin,
        )
        for ((_, file) in byPlugin) {
            val cfg = xml("/META-INF/$file")
            val ext = cfg.getElementsByTagName("defaultLiveTemplates")
            assertEquals(file, 1, ext.length)
            val path = (ext.item(0) as Element).getAttribute("file")
            assertNotNull(path, javaClass.getResource(path))
            assertEquals("/liveTemplates/" + file.removeSuffix(".xml") + ".xml", path)
        }
        val plain = doc.getElementsByTagName("depends")
        val hard = (0 until plain.length).map { plain.item(it) as Element }.filter { it.getAttribute("optional") != "true" }.map { it.textContent.trim() }
        assertEquals(listOf("com.intellij.modules.platform", "com.intellij.modules.json"), hard)
    }

    @Test
    fun everyLanguageUsesTheSharedPrefixes() {
        val shared = snippets("java").map { it.prefix }.toSet()
        assertEquals(58, shared.size)
        for (lang in listOf("csharp", "python", "rust", "node", "go", "zig", "lua", "godot")) {
            val got = snippets(lang).map { it.prefix }
            assertEquals(lang, got.size, got.toSet().size)
            assertTrue("$lang has prefixes java doesnt: " + (got.toSet() - shared), shared.containsAll(got))
            assertTrue("$lang only has ${got.size}", got.size >= 55)
            for (must in listOf("goome-overlay", "goome-app", "goome-loop", "goome-button", "goome-slider", "goome-theme-preset")) assertTrue("$lang is missing $must", must in got)
        }
    }

    @Test
    fun snippetsHaveNoComments() {
        for (lang in listOf("rust", "node", "go", "zig", "lua", "godot")) for (s in snippets(lang)) {
            for (line in s.text.lines()) {
                val t = line.trim()
                assertFalse("$lang ${s.prefix}: $t", t.startsWith("//") || t.startsWith("--") || t.startsWith("#") || t.startsWith("/*"))
            }
        }
    }

    @Test
    fun riderTemplatesMatchSnippets() {
        val ds = dotSettings()
        same(snippets("csharp"), ds.getValue("InCSharpFile"))
        same(snippets("c"), ds.getValue("InCppFile"))
    }

    @Test
    fun everyPrefixStartsWithGoome() {
        for (lang in listOf("c", "csharp", "python", "java", "rust", "node", "go", "zig", "lua", "godot")) for (s in snippets(lang)) assertTrue(s.prefix, s.prefix.startsWith("goome-"))
    }

    @Test
    fun sameAsVscodeGoome() {
        val vs = sibling("vscode-goome")
        assumeTrue("vscode-goome isnt next to this repo", vs != null)
        val shared = setOf("c.json", "csharp.json", "python.json", "java.json")
        for (f in File(root, "snippets").listFiles()!!.filter { it.name in shared } + File(root, "schemas").listFiles()!!) {
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
