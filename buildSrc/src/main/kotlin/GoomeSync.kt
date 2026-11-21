import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.util.UUID

object GoomeSync {
    const val DOCS_URL = "https://goome.wtf/docs/"
    val BUILDER_FILES = listOf("model.js", "codegen.js", "elements.js", "app.js", "stage.js", "vars.js", "style.css", "elements.css")
    val LANGS = listOf("c", "csharp", "python", "java", "rust", "node", "go", "zig", "lua", "godot")
    val TEMPLATE_LANGS = listOf("c", "java", "python", "rust", "node", "go", "zig", "lua", "godot")

    sealed class Part {
        data class Text(val s: String) : Part()
        data class Stop(val n: Int, val def: String?, val choices: List<String>?) : Part()
    }

    data class Snippet(val name: String, val prefix: String, val description: String, val parts: List<Part>)

    fun find(env: String?, root: File, names: List<String>): File? {
        val tries = mutableListOf<File>()
        if (!env.isNullOrBlank()) tries += File(env)
        for (n in names) {
            tries += File(root.parentFile, n)
            tries += File(root, "deps/$n")
        }
        return tries.firstOrNull { it.exists() }
    }

    fun parseBody(text: String): List<Part> {
        val out = mutableListOf<Part>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) out += Part.Text(buf.toString())
            buf.setLength(0)
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length && text[i + 1] in "\$}\\") {
                buf.append(text[i + 1])
                i += 2
                continue
            }
            if (c == '$' && i + 1 < text.length && text[i + 1].isDigit()) {
                var j = i + 1
                while (j < text.length && text[j].isDigit()) j++
                flush()
                out += Part.Stop(text.substring(i + 1, j).toInt(), null, null)
                i = j
                continue
            }
            if (c == '$' && i + 2 < text.length && text[i + 1] == '{' && text[i + 2].isDigit()) {
                var j = i + 2
                while (j < text.length && text[j].isDigit()) j++
                val n = text.substring(i + 2, j).toInt()
                when (text.getOrNull(j)) {
                    '}' -> {
                        flush()
                        out += Part.Stop(n, null, null)
                        i = j + 1
                        continue
                    }
                    ':' -> {
                        val def = StringBuilder()
                        var k = j + 1
                        while (k < text.length && text[k] != '}') {
                            if (text[k] == '\\' && k + 1 < text.length) k++
                            def.append(text[k])
                            k++
                        }
                        flush()
                        out += Part.Stop(n, def.toString(), null)
                        i = k + 1
                        continue
                    }
                    '|' -> {
                        val end = text.indexOf("|}", j + 1)
                        if (end > 0) {
                            flush()
                            out += Part.Stop(n, null, text.substring(j + 1, end).split(","))
                            i = end + 2
                            continue
                        }
                    }
                }
            }
            buf.append(c)
            i++
        }
        flush()
        return out
    }

    @Suppress("UNCHECKED_CAST")
    fun readSnippets(file: File): List<Snippet> {
        val obj = JsonSlurper().parse(file) as Map<String, Map<String, Any>>
        return obj.map { (name, s) ->
            val body = s["body"]
            val text = if (body is List<*>) body.joinToString("\n") else body.toString()
            Snippet(name, s["prefix"].toString(), (s["description"] ?: "").toString(), parseBody(text))
        }
    }

    fun spaces(parts: List<Part>): List<Part> = parts.map { p ->
        if (p is Part.Text) Part.Text(p.s.replace(Regex("(?m)^\t+")) { "    ".repeat(it.value.length) }) else p
    }

    class Field(val n: Int, var def: String?, var choices: List<String>?)

    fun fields(parts: List<Part>): List<Field> {
        val map = sortedMapOf<Int, Field>()
        for (p in parts) if (p is Part.Stop && p.n > 0) {
            val f = map.getOrPut(p.n) { Field(p.n, null, null) }
            if (p.def != null && f.def == null) f.def = p.def
            if (p.choices != null && f.choices == null) f.choices = p.choices
        }
        return map.values.toList()
    }

    fun templateText(parts: List<Part>, name: (Int) -> String): String {
        val sb = StringBuilder()
        for (p in parts) when (p) {
            is Part.Text -> sb.append(p.s.replace("$", "$$"))
            is Part.Stop -> sb.append(if (p.n == 0) "\$END\$" else "$" + name(p.n) + "$")
        }
        return sb.toString()
    }

    fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun attr(s: String): String {
        val sb = StringBuilder()
        for (c in s) when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            '\n' -> sb.append("&#10;")
            '\t' -> sb.append("&#9;")
            '\r' -> {}
            else -> sb.append(c)
        }
        return sb.toString()
    }

    fun body(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    val CONTEXTS = mapOf(
        "c" to listOf("OC_DECLARATION_C", "OC_STATEMENT_C", "OC_DECLARATION_CPP", "OC_STATEMENT_CPP"),
        "java" to listOf("JAVA_CODE"),
        "python" to listOf("Python"),
        "rust" to listOf("RUST_ITEM", "RUST_STATEMENT"),
        "node" to listOf("JS_TOP_LEVEL_STATEMENT", "JS_STATEMENT", "TS_TOP_LEVEL_STATEMENT", "TS_STATEMENT"),
        "go" to listOf("GO_FILE", "GO_STATEMENT"),
        "zig" to listOf("OTHER"),
        "lua" to listOf("LUA_CODE"),
        "godot" to listOf("GDSCRIPT"),
    )

    val SPACES = setOf("python", "rust", "zig", "lua")
    val NO_REFORMAT = setOf("python", "zig", "lua", "godot")

    val GROUPS = mapOf("c" to "goome C", "java" to "goome Java", "python" to "goome Python",
        "rust" to "goome Rust", "node" to "goome JavaScript", "go" to "goome Go", "zig" to "goome Zig", "lua" to "goome Lua", "godot" to "goome GDScript")

    fun liveTemplates(lang: String, snippets: List<Snippet>): String {
        val sb = StringBuilder()
        sb.append("<templateSet group=\"").append(GROUPS.getValue(lang)).append("\">\n")
        for (s in snippets) {
            val parts = if (lang in SPACES) spaces(s.parts) else s.parts
            val value = templateText(parts) { "P$it" }
            val reformat = lang !in NO_REFORMAT
            sb.append("  <template name=\"").append(attr(s.prefix)).append("\" value=\"").append(attr(value))
                .append("\" description=\"").append(attr(s.description)).append("\" toReformat=\"").append(reformat)
                .append("\" toShortenFQNames=\"true\">\n")
            for (f in fields(parts)) {
                val expr = when {
                    f.choices != null -> "enum(" + f.choices!!.joinToString(",") { quote(it) } + ")"
                    f.def != null -> quote(f.def!!)
                    else -> ""
                }
                sb.append("    <variable name=\"P").append(f.n).append("\" expression=\"").append(attr(expr))
                    .append("\" defaultValue=\"\" alwaysStopAt=\"true\" />\n")
            }
            sb.append("    <context>\n")
            for (c in CONTEXTS.getValue(lang)) sb.append("      <option name=\"").append(c).append("\" value=\"true\" />\n")
            sb.append("    </context>\n  </template>\n")
        }
        sb.append("</templateSet>\n")
        return sb.toString()
    }

    fun guid(seed: String) = UUID.nameUUIDFromBytes(seed.toByteArray()).toString().replace("-", "").uppercase()

    fun dotSettings(byLang: Map<String, List<Snippet>>): String {
        val sb = StringBuilder()
        sb.append("<wpf:ResourceDictionary xml:space=\"preserve\" xmlns:x=\"http://schemas.microsoft.com/winfx/2006/xaml\" xmlns:s=\"clr-namespace:System;assembly=mscorlib\" xmlns:ss=\"urn:shemas-jetbrains-com:settings-storage-xaml\" xmlns:wpf=\"http://schemas.microsoft.com/winfx/2006/xaml/presentation\">\n")
        for ((lang, snippets) in byLang) {
            val scopeType = if (lang == "csharp") "InCSharpFile" else "InCppFile"
            for (s in snippets) {
                val id = guid("goome/$lang/${s.prefix}")
                val scope = guid("goome/$lang/scope")
                val key = "/Default/PatternsAndTemplates/LiveTemplates/Template/=$id"
                fun line(type: String, path: String, value: String) {
                    sb.append("\t<s:").append(type).append(" x:Key=\"").append(key).append(path).append("\">").append(value).append("</s:").append(type).append(">\n")
                }
                line("Boolean", "/@KeyIndexDefined", "True")
                line("Boolean", "/Applicability/=Live/@EntryIndexedValue", "True")
                line("String", "/Categories/=goome/@EntryIndexedValue", "goome")
                line("String", "/Description/@EntryValue", body(s.description))
                for ((i, f) in fields(s.parts).withIndex()) {
                    line("Boolean", "/Field/=p${f.n}/@KeyIndexDefined", "True")
                    val expr = when {
                        f.choices != null -> "list(" + quote(f.choices!!.joinToString(",") { it.replace(",", "\\,") }) + ")"
                        f.def != null -> "constant(" + quote(f.def!!) + ")"
                        else -> null
                    }
                    if (expr != null) line("String", "/Field/=p${f.n}/Expression/@EntryValue", body(expr))
                    line("Int64", "/Field/=p${f.n}/Order/@EntryValue", i.toString())
                }
                line("Boolean", "/Reformat/@EntryValue", "True")
                line("Boolean", "/Scope/=$scope/@KeyIndexDefined", "True")
                if (lang == "csharp") line("String", "/Scope/=$scope/CustomProperties/=minimumLanguageVersion/@EntryIndexedValue", "2.0")
                line("String", "/Scope/=$scope/Type/@EntryValue", scopeType)
                line("String", "/Shortcut/@EntryValue", body(s.prefix))
                line("Boolean", "/ShortenQualifiedReferences/@EntryValue", "True")
                line("String", "/Text/@EntryValue", body(templateText(s.parts) { "p$it" }))
            }
        }
        sb.append("</wpf:ResourceDictionary>\n")
        return sb.toString()
    }

    data class Fn(val sig: String, val note: String)

    fun parseHeader(text: String): Map<String, Fn> {
        val out = linkedMapOf<String, Fn>()
        val re = Regex("^([A-Za-z_][\\w\\s*]*?[\\s*])(goome_\\w+)\\s*\\(([^;]*)\\)\\s*;\\s*(?://\\s*(.*))?$")
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("#") || line.startsWith("//") || line.startsWith("typedef")) continue
            val m = re.find(line) ?: continue
            val sig = (m.groupValues[1].trim() + " " + m.groupValues[2] + "(" + m.groupValues[3].trim() + ");").replace(Regex("\\*\\s+goome_"), "*goome_")
            out[m.groupValues[2]] = Fn(sig, m.groupValues[4].trim())
        }
        return out
    }

    fun docPages(docs: File?): Map<String, String> {
        val pages = linkedMapOf<String, String>()
        val dir = docs?.resolve("pages") ?: return pages
        if (!dir.isDirectory) return pages
        val files = dir.listFiles { f -> f.name.endsWith(".md") }!!.sortedBy { it.name }
        val head = Regex("^#{2,4}\\s+(goome_\\w+)\\s*$")
        for (f in files.filter { it.name.startsWith("api-") }) {
            val slug = f.name.removeSuffix(".md")
            for (line in f.readLines()) {
                val m = head.find(line) ?: continue
                pages.putIfAbsent(m.groupValues[1], "$slug/#${m.groupValues[1]}")
            }
        }
        val call = Regex("\\b(goome_\\w+)\\s*\\(")
        for (f in files.filterNot { it.name.startsWith("api-") || it.name == "index.md" }) {
            val slug = f.name.removeSuffix(".md")
            for (m in call.findAll(f.readText())) pages.putIfAbsent(m.groupValues[1], "$slug/")
        }
        return pages
    }

    fun hoverJson(header: File, docs: File?): String {
        val text = header.readText()
        val fns = parseHeader(text)
        val pages = docPages(docs)
        val version = Regex("#define GOOME_VERSION \"([^\"]+)\"").find(text)?.groupValues?.get(1) ?: ""
        val functions = linkedMapOf<String, Any>()
        for ((name, fn) in fns) functions[name] = linkedMapOf("sig" to fn.sig, "note" to fn.note, "url" to DOCS_URL + (pages[name] ?: ""))
        return JsonOutput.prettyPrint(JsonOutput.toJson(linkedMapOf("version" to version, "docs" to DOCS_URL, "functions" to functions))) + "\n"
    }

    fun run(root: File, out: File, pluginId: String) {
        val docs = find(System.getenv("GOOME_DOCS"), root, listOf("goome-docs", "docs"))
        val cgoome = find(System.getenv("GOOME_C"), root, listOf("c-goome"))
        val website = find(System.getenv("GOOME_WEBSITE"), root, listOf("website", "website-source"))
        val builder = listOfNotNull(docs?.resolve("builder"), website?.resolve("src/builder")).firstOrNull { it.resolve("model.js").exists() }
            ?: throw IllegalStateException("cant find the builder. put website next to this folder, in deps/, or set GOOME_WEBSITE")
        val header = cgoome?.resolve("include/goome.h")
        if (header == null || !header.exists())
            throw IllegalStateException("cant find c-goome/include/goome.h. put c-goome next to this folder, in deps/, or set GOOME_C")

        out.deleteRecursively()
        val goome = out.resolve("goome")
        for (f in BUILDER_FILES) builder.resolve(f).copyTo(goome.resolve("builder/$f"), true)
        goome.resolve("hover.json").writeText(hoverJson(header, docs))
        root.resolve("schemas").listFiles()!!.forEach { it.copyTo(out.resolve("schemas/${it.name}"), true) }

        for (lang in LANGS) root.resolve("snippets/$lang.json").copyTo(goome.resolve("snippets/$lang.json"), true)
        val snippets = LANGS.associateWith { readSnippets(root.resolve("snippets/$it.json")) }
        val lt = out.resolve("liveTemplates")
        lt.mkdirs()
        for (lang in TEMPLATE_LANGS) lt.resolve("goome-$lang.xml").writeText(liveTemplates(lang, snippets.getValue(lang)))
        val ds = out.resolve("dotnet/Extensions/$pluginId/settings/goome.DotSettings")
        ds.parentFile.mkdirs()
        ds.writeText(dotSettings(linkedMapOf("csharp" to snippets.getValue("csharp"), "c" to snippets.getValue("c"))))
    }
}
