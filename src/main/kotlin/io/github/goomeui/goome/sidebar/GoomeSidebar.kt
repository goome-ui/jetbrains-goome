package io.github.goomeui.goome.sidebar

import com.google.gson.JsonParser
import io.github.goomeui.goome.Goome
import io.github.goomeui.goome.newproject.GoomeCli

object GoomeSidebar {
    enum class Kind { GROUP, ACTION, FILE, LANG, TEMPLATE, SNIPPET, EMPTY }

    data class Item(
        val id: String,
        val label: String,
        val kind: Kind,
        val detail: String = "",
        val arg: String = "",
        val arg2: String = "",
        val open: Boolean = false,
        val children: List<Item> = emptyList(),
    )

    data class FileRef(val url: String, val path: String)

    data class Snippet(val prefix: String, val description: String, val body: String)

    const val NEW_PROJECT = "new"
    const val BUILDER = "builder"
    const val BUILD_WEB = "buildweb"
    const val SEARCH_DOCS = "search"
    const val DOCS = "docs"

    val SNIPPET_LANGS = linkedMapOf(
        "c" to "C", "csharp" to "C#", "python" to "Python", "java" to "Java",
        "rust" to "Rust", "node" to "JavaScript", "go" to "Go", "zig" to "Zig", "lua" to "Lua", "godot" to "GDScript",
    )
    val LIVE_GROUPS = mapOf(
        "c" to "goome C", "java" to "goome Java", "python" to "goome Python",
        "rust" to "goome Rust", "node" to "goome JavaScript", "go" to "goome Go", "zig" to "goome Zig", "lua" to "goome Lua", "godot" to "goome GDScript",
    )
    private val EXTS = mapOf(
        "c" to "c", "h" to "c", "cpp" to "c", "cc" to "c", "cxx" to "c", "hpp" to "c", "hh" to "c",
        "cs" to "csharp", "py" to "python", "pyw" to "python", "java" to "java",
        "rs" to "rust", "js" to "node", "mjs" to "node", "cjs" to "node", "jsx" to "node", "ts" to "node", "mts" to "node", "cts" to "node", "tsx" to "node",
        "go" to "go", "zig" to "zig", "lua" to "lua", "gd" to "godot",
    )

    private val snippetCache = mutableMapOf<String, List<Snippet>>()

    fun snippetLang(ext: String?): String = EXTS[ext?.lowercase()] ?: "c"

    fun quickActions() = listOf(
        Item("quick/new", "New Project", Kind.ACTION, "make a goome project with goome-cli", NEW_PROJECT),
        Item("quick/builder", "Open UI builder", Kind.ACTION, "in the browser", BUILDER),
        Item("quick/buildweb", "Build for Web", Kind.ACTION, "compile a C app to WebAssembly", BUILD_WEB),
        Item("quick/search", "Search docs", Kind.ACTION, "find a goome function", SEARCH_DOCS),
        Item("quick/docs", "Open docs", Kind.ACTION, Goome.DOCS_URL, DOCS),
    )

    fun templates(): List<Item> = GoomeCli.LANGS.map { l ->
        Item("templates/${l.id}", l.label, Kind.LANG, l.detail, l.id, children = GoomeCli.TEMPLATES.map { t ->
            Item("templates/${l.id}/${t.id}", t.label, Kind.TEMPLATE, t.detail, l.id, t.id)
        })
    }

    fun isBuilderFile(name: String) = name.endsWith(Goome.PROJECT_SUFFIX) || name.endsWith(Goome.THEME_SUFFIX)

    private fun dirOf(path: String, base: String?): String {
        val p = path.replace('\\', '/')
        val dir = p.substringBeforeLast('/', "")
        val b = base?.replace('\\', '/')?.trimEnd('/')
        return when {
            b.isNullOrEmpty() -> dir
            dir == b -> ""
            dir.startsWith("$b/") -> dir.removePrefix("$b/")
            else -> dir
        }
    }

    fun files(refs: List<FileRef>, base: String?): List<Item> = refs
        .filter { isBuilderFile(it.path) }
        .distinctBy { it.url }
        .map { Triple(it, dirOf(it.path, base), it.path.replace('\\', '/').substringAfterLast('/')) }
        .sortedWith(compareBy({ it.second.lowercase() }, { it.third.lowercase() }))
        .map { (ref, dir, name) -> Item("files/${ref.url}", name, Kind.FILE, dir, ref.url) }

    fun readSnippets(lang: String): List<Snippet> = synchronized(snippetCache) {
        snippetCache.getOrPut(lang) {
            if (lang !in SNIPPET_LANGS) return@getOrPut emptyList()
            val text = GoomeSidebar::class.java.getResourceAsStream("/goome/snippets/$lang.json")?.use { String(it.readAllBytes(), Charsets.UTF_8) }
                ?: return@getOrPut emptyList()
            JsonParser.parseString(text).asJsonObject.entrySet().map { (_, v) ->
                val o = v.asJsonObject
                val body = o.get("body")
                val joined = if (body.isJsonArray) body.asJsonArray.joinToString("\n") { it.asString } else body.asString
                Snippet(o.get("prefix").asString, o.get("description").asString, joined)
            }
        }
    }

    fun expand(body: String): String {
        val defs = mutableMapOf<String, String>()
        val out = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length && body[i + 1] in "\$}\\") {
                out.append(body[i + 1])
                i += 2
                continue
            }
            if (c == '$' && i + 1 < body.length && body[i + 1].isDigit()) {
                var j = i + 1
                while (j < body.length && body[j].isDigit()) j++
                out.append(defs[body.substring(i + 1, j)] ?: "")
                i = j
                continue
            }
            if (c == '$' && i + 2 < body.length && body[i + 1] == '{' && body[i + 2].isDigit()) {
                var j = i + 2
                while (j < body.length && body[j].isDigit()) j++
                val n = body.substring(i + 2, j)
                when (body.getOrNull(j)) {
                    '}' -> {
                        out.append(defs[n] ?: "")
                        i = j + 1
                        continue
                    }
                    ':' -> {
                        var depth = 0
                        var k = j + 1
                        val def = StringBuilder()
                        while (k < body.length && !(body[k] == '}' && depth == 0)) {
                            if (body[k] == '{') depth++
                            if (body[k] == '}') depth--
                            def.append(body[k])
                            k++
                        }
                        val v = expand(def.toString())
                        defs.putIfAbsent(n, v)
                        out.append(v)
                        i = k + 1
                        continue
                    }
                    '|' -> {
                        val end = body.indexOf("|}", j + 1)
                        if (end > 0) {
                            val v = body.substring(j + 1, end).split(",")[0]
                            defs.putIfAbsent(n, v)
                            out.append(v)
                            i = end + 2
                            continue
                        }
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    fun snippets(lang: String): List<Item> = readSnippets(lang).map {
        Item("snippets/$lang/${it.prefix}", it.prefix, Kind.SNIPPET, it.description, lang, it.prefix)
    }

    fun root(refs: List<FileRef>, base: String?, lang: String): List<Item> {
        val files = files(refs, base)
        val shown = files.ifEmpty { listOf(Item("files/none", "none yet, make one in the UI builder", Kind.EMPTY, arg = BUILDER)) }
        return listOf(
            Item("quick", "Quick actions", Kind.GROUP, open = true, children = quickActions()),
            Item("files", "Builder files", Kind.GROUP, files.size.toString(), open = true, children = shown),
            Item("templates", "Templates", Kind.GROUP, open = true, children = templates()),
            Item("snippets", "Snippets", Kind.GROUP, SNIPPET_LANGS[lang] ?: "C", children = snippets(lang)),
        )
    }
}
