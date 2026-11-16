package io.github.goomeui.goome

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.goomeui.goome.sidebar.GoomeSidebar
import io.github.goomeui.goome.sidebar.GoomeSidebar.FileRef
import io.github.goomeui.goome.sidebar.GoomeSidebar.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SidebarTest {
    private val root = File(System.getProperty("goome.root") ?: ".")

    @Test
    fun rootHasFourGroups() {
        val items = GoomeSidebar.root(emptyList(), "/p", "c")
        assertEquals(listOf("Quick actions", "Builder files", "Templates", "Snippets"), items.map { it.label })
        assertTrue(items.all { it.kind == Kind.GROUP })
        assertEquals(listOf(true, true, true, false), items.map { it.open })
        assertEquals("0", items[1].detail)
        assertEquals(listOf(Kind.EMPTY), items[1].children.map { it.kind })
        assertEquals("C", items[3].detail)
    }

    @Test
    fun quickActions() {
        val q = GoomeSidebar.quickActions()
        assertEquals(listOf("New Project", "Open UI builder", "Search docs", "Open docs"), q.map { it.label })
        assertEquals(listOf(GoomeSidebar.NEW_PROJECT, GoomeSidebar.BUILDER, GoomeSidebar.SEARCH_DOCS, GoomeSidebar.DOCS), q.map { it.arg })
    }

    @Test
    fun templatesPerLanguage() {
        val langs = GoomeSidebar.templates()
        assertEquals(listOf("C", "C#", "Python", "Java", "Godot", "Rust", "Node", "Go", "Zig", "Lua"), langs.map { it.label })
        for (l in langs) {
            assertEquals(listOf("app", "overlay", "mod-menu", "hud"), l.children.map { it.label })
            for (t in l.children) {
                assertEquals(Kind.TEMPLATE, t.kind)
                assertEquals(l.arg, t.arg)
                assertEquals(t.label, t.arg2)
            }
        }
        val ids = langs.flatMap { l -> listOf(l.id) + l.children.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun filesAreFilteredSortedAndLabelled() {
        val refs = listOf(
            FileRef("file:///p/ui/b.goome.json", "/p/ui/b.goome.json"),
            FileRef("file:///p/a.goome-theme.json", "/p/a.goome-theme.json"),
            FileRef("file:///p/ui/b.goome.json", "/p/ui/b.goome.json"),
            FileRef("file:///p/package.json", "/p/package.json"),
            FileRef("file:///elsewhere/c.goome.json", "/elsewhere/c.goome.json"),
        )
        val files = GoomeSidebar.files(refs, "/p")
        assertEquals(listOf("a.goome-theme.json", "c.goome.json", "b.goome.json"), files.map { it.label })
        assertEquals(listOf("", "/elsewhere", "ui"), files.map { it.detail })
        assertTrue(files.all { it.kind == Kind.FILE && it.arg.startsWith("file:///") })
        assertEquals("3", GoomeSidebar.root(refs, "/p", "c")[1].detail)
        assertEquals(listOf("x.goome.json"), GoomeSidebar.files(listOf(FileRef("u", "C:\\w\\x.goome.json")), "C:\\w").map { it.label })
    }

    @Test
    fun snippetLanguageFollowsTheFile() {
        assertEquals("c", GoomeSidebar.snippetLang("h"))
        assertEquals("c", GoomeSidebar.snippetLang("cpp"))
        assertEquals("csharp", GoomeSidebar.snippetLang("cs"))
        assertEquals("python", GoomeSidebar.snippetLang("py"))
        assertEquals("java", GoomeSidebar.snippetLang("JAVA"))
        assertEquals("rust", GoomeSidebar.snippetLang("rs"))
        assertEquals("node", GoomeSidebar.snippetLang("ts"))
        assertEquals("node", GoomeSidebar.snippetLang("mjs"))
        assertEquals("go", GoomeSidebar.snippetLang("go"))
        assertEquals("zig", GoomeSidebar.snippetLang("zig"))
        assertEquals("lua", GoomeSidebar.snippetLang("lua"))
        assertEquals("godot", GoomeSidebar.snippetLang("gd"))
        assertEquals("c", GoomeSidebar.snippetLang("md"))
        assertEquals("c", GoomeSidebar.snippetLang(null))
    }

    @Test
    fun cheatSheetMatchesTheSnippetFiles() {
        for (lang in GoomeSidebar.SNIPPET_LANGS.keys) {
            val raw = JsonParser.parseString(File(root, "snippets/$lang.json").readText()).asJsonObject
            val prefixes = raw.entrySet().map { it.value.asJsonObject.get("prefix").asString }
            val items = GoomeSidebar.snippets(lang)
            assertEquals(prefixes, items.map { it.label })
            assertTrue(items.all { it.kind == Kind.SNIPPET && it.arg == lang && it.arg2 == it.label && it.detail.isNotEmpty() })
        }
        assertEquals(emptyList<Any>(), GoomeSidebar.snippets("ruby"))
    }

    @Test
    fun snippetsExpandToPlainCode() {
        assertEquals("ui.button(\"Click me\")\nx", GoomeSidebar.expand("ui.button(\"\${1:Click me}\")\n\${2|x,y|}\$0"))
        assertEquals("a a b", GoomeSidebar.expand("\${1:a} \$1 \${2:b}"))
        assertEquals("\$5 }", GoomeSidebar.expand("\\\$5 \\}"))
        for (lang in GoomeSidebar.SNIPPET_LANGS.keys) for (s in GoomeSidebar.readSnippets(lang)) {
            val t = GoomeSidebar.expand(s.body)
            assertFalse("$lang ${s.prefix}: $t", Regex("\\$\\{\\d|\\$\\d").containsMatchIn(t))
        }
    }

    @Test
    fun iconsAreThere() {
        val res = File(root, "src/main/resources")
        val tw = "icons/expui/toolwindow/toolWindowGoome"
        for (p in listOf("icons/toolWindowGoome.svg", "icons/toolWindowGoome_dark.svg", "$tw.svg", "${tw}_dark.svg", "$tw@20x20.svg", "$tw@20x20_dark.svg",
            "icons/goome.svg", "icons/goome_dark.svg", "META-INF/pluginIcon.svg", "META-INF/pluginIcon_dark.svg")) {
            val f = File(res, p)
            assertTrue(p, f.isFile)
            val size = Regex("width=\"(\\d+)\" height=\"(\\d+)\"").find(f.readText())!!.groupValues
            val want = when {
                p.startsWith("icons/toolWindow") -> "13"
                p.startsWith("META-INF") -> "40"
                "@20x20" in p -> "20"
                else -> "16"
            }
            assertEquals(p, listOf(want, want), size.drop(1))
        }
        val map = JsonParser.parseString(File(res, "GoomeIconMappings.json").readText()).asJsonObject
        val pairs = mutableListOf<Pair<String, String>>()
        fun walk(o: JsonObject, dir: String) {
            for ((k, v) in o.entrySet()) {
                if (v.isJsonObject) walk(v.asJsonObject, "$dir$k/") else pairs += "$dir$k" to v.asString
            }
        }
        walk(map, "")
        assertEquals(listOf("$tw.svg" to "icons/toolWindowGoome.svg"), pairs)
        for ((newUi, classic) in pairs) {
            assertTrue(newUi, File(res, newUi).isFile)
            assertTrue(classic, File(res, classic).isFile)
        }
    }
}
