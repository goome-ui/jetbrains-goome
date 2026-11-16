package io.github.goomeui.goome

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection
import io.github.goomeui.goome.docs.GoomeDocTarget
import io.github.goomeui.goome.docs.GoomeDocTargets
import io.github.goomeui.goome.docs.GoomeDocs
import io.github.goomeui.goome.preview.GoomePreviewProvider
import io.github.goomeui.goome.preview.PreviewPage
import io.github.goomeui.goome.sidebar.GoomePanel
import com.intellij.openapi.wm.ToolWindowEP
import java.io.File

class PluginTest : BasePlatformTestCase() {
    private val root = File(System.getProperty("goome.root") ?: ".")

    private fun add(name: String, path: String = name) = myFixture.addFileToProject(path, File(root, "src/test/testData/$name").readText()).virtualFile

    override fun setUp() {
        super.setUp()
        VfsRootAccess.allowRootAccess(testRootDisposable, root.absolutePath, PathManager.getPluginsPath())
    }

    fun testFileTypes() {
        val ftm = FileTypeManager.getInstance()
        assertSame(GoomeProjectFileType.INSTANCE, ftm.getFileTypeByFileName("demo.goome.json"))
        assertSame(GoomeThemeFileType.INSTANCE, ftm.getFileTypeByFileName("midnight.goome-theme.json"))
        assertNotSame(GoomeProjectFileType.INSTANCE, ftm.getFileTypeByFileName("package.json"))
    }

    fun testSchemasAreFound() {
        val providers = JsonSchemaProviderFactory.EP_NAME.extensionList.filterIsInstance<GoomeSchemas>().flatMap { it.getProviders(project) }
        assertEquals(2, providers.size)
        for (p in providers) assertNotNull(p.name, p.schemaFile)
        val file = add("demo.goome.json")
        val schema = JsonSchemaService.Impl.get(project).getSchemaFilesForFile(file)
        assertTrue(schema.any { it.name == "goome-project.schema.json" })
        val theme = add("midnight.goome-theme.json")
        assertTrue(JsonSchemaService.Impl.get(project).getSchemaFilesForFile(theme).any { it.name == "goome-theme.schema.json" })
    }

    fun testSchemaCatchesTypos() {
        myFixture.enableInspections(JsonSchemaComplianceInspection::class.java)
        myFixture.configureByText("bad.goome.json", """{"format": "goome-project", "mode": "sideways", "root": [{"type": "buttn"}]}""")
        val warnings = myFixture.doHighlighting().filter { it.severity >= HighlightSeverity.WEAK_WARNING }
        assertTrue(warnings.joinToString { it.description ?: "" }, warnings.size >= 2)
        myFixture.configureFromExistingVirtualFile(add("demo.goome.json", "ok/demo.goome.json"))
        val clean = myFixture.doHighlighting().filter { it.severity >= HighlightSeverity.WARNING }
        assertEquals(clean.joinToString { it.description ?: "" }, 0, clean.size)
    }

    fun testJavaTemplatesAreLoaded() {
        val settings = TemplateSettings.getInstance()
        assertNotNull(settings.getTemplate("goome-button", "goome Java"))
        assertNotNull(settings.getTemplate("goome-overlay", "goome Java"))
        assertEquals(58, settings.templates.count { it.groupName == "goome Java" })
    }

    fun testJavaTemplateExpands() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        myFixture.configureByText("A.java", "class A { void f() { goome-button<caret> } }")
        myFixture.type('\t')
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("ui.button(\"Click me\")"))
    }

    fun testDocsForGoomeFunctions() {
        val fn = GoomeDocs.functions["goome_open"]
        assertNotNull(fn)
        assertTrue(fn!!.sig.contains("goome_open("))
        assertTrue(GoomeDocs.functions.size > 100)
        val file = myFixture.configureByText("main.c", "int main(void) { goome_close(g); }")
        val at = file.text.indexOf("goome_close") + 3
        val targets = GoomeDocTargets().documentationTargets(file, at)
        assertEquals(1, targets.size)
        assertTrue(GoomeDocs.html(GoomeDocs.functions.getValue("goome_close")).contains("goome_close"))
        assertEquals(0, GoomeDocTargets().documentationTargets(file, 2).size)
    }

    fun testDocsForBindingNames() {
        val rust = myFixture.configureByText("main.rs", "use goome::Goome;\nfn f() { ui.slider_int(); g.close(); }")
        val at = rust.text.indexOf("slider_int") + 2
        val targets = GoomeDocTargets().documentationTargets(rust, at)
        assertEquals(1, targets.size)
        assertEquals("goome_ui_slider_int", (targets[0] as GoomeDocTarget).fn.name)
        val go = myFixture.configureByText("main.go", "import \"goome\"\nfunc f() { ui.SliderInt(); g.Close() }")
        assertEquals("goome_ui_slider_int", (GoomeDocTargets().documentationTargets(go, go.text.indexOf("SliderInt") + 1)[0] as GoomeDocTarget).fn.name)
        assertEquals("goome_close", (GoomeDocTargets().documentationTargets(go, go.text.indexOf("Close") + 1)[0] as GoomeDocTarget).fn.name)
        val js = myFixture.configureByText("app.js", "const goome = require('goome');\nui.sliderInt(1)")
        assertEquals(1, GoomeDocTargets().documentationTargets(js, js.text.indexOf("sliderInt") + 1).size)
        val lua = myFixture.configureByText("x.lua", "local goome = require('goome')\nui:slider_int()")
        assertEquals(1, GoomeDocTargets().documentationTargets(lua, lua.text.indexOf("slider_int") + 1).size)
        val unrelated = myFixture.configureByText("other.rs", "fn f() { ui.slider_int(); }")
        assertEquals(0, GoomeDocTargets().documentationTargets(unrelated, unrelated.text.indexOf("slider_int") + 1).size)
        val java = myFixture.configureByText("A.java", "import goome; class A { void f() { ui.sliderInt(); } }")
        assertEquals(0, GoomeDocTargets().documentationTargets(java, java.text.indexOf("sliderInt") + 1).size)
    }

    fun testWordAt() {
        val t = "x = goome_ui_button(u, \"hi\");"
        assertEquals("goome_ui_button", GoomeDocs.wordAt(t, 4))
        assertEquals("goome_ui_button", GoomeDocs.wordAt(t, 10))
        assertEquals("goome_ui_button", GoomeDocs.wordAt(t, 19))
        assertNull(GoomeDocs.wordAt(t, 0))
        assertNull(GoomeDocs.wordAt("", 0))
    }

    fun testPreviewPage() {
        val html = PreviewPage.html("SEND(m)")
        assertTrue(html.contains("window.goomeSend = function (m) { SEND(m) };"))
        for (lang in listOf("c", "cs", "py", "java", "gd", "rust", "node", "go", "zig", "lua")) assertTrue(html.contains("data-copy=\"$lang\""))
        assertEquals(10, Regex("data-copy=").findAll(html).count())
        assertTrue(html.contains("GMCode"))
        assertTrue(html.contains("window.goomeLoad"))
        assertTrue(html.contains("Open in UI builder"))
        assertFalse(html.substringAfter("<body>").contains("src=\""))
    }

    fun testPreviewOnlyForProjects() {
        val provider = GoomePreviewProvider()
        val theme = add("midnight.goome-theme.json")
        assertFalse(provider.accept(project, theme))
        assertEquals(GoomeProjectFileType.INSTANCE, add("demo.goome.json").fileType)
    }

    fun testRiderSettingsLayerIsBundled() {
        val path = "/dotnet/Extensions/io.github.goomeui.goome/settings/goome.DotSettings"
        val text = javaClass.getResourceAsStream(path)?.use { String(it.readAllBytes()) }
        assertNotNull(text)
        assertTrue(text!!.contains(">goome-overlay<"))
        assertTrue(text.contains(">InCSharpFile<"))
        assertTrue(text.contains(">InCppFile<"))
    }

    fun testToolWindowIsOnTheLeft() {
        val ep = ToolWindowEP.EP_NAME.extensionList.single { it.id == "goome" }
        assertEquals("left", ep.anchor)
        assertEquals("/icons/toolWindowGoome.svg", ep.icon)
        assertEquals("io.github.goomeui.goome.sidebar.GoomeToolWindowFactory", ep.factoryClass)
    }

    fun testToolWindowPanel() {
        val demo = add("demo.goome.json", "ui/demo.goome.json")
        add("midnight.goome-theme.json")
        val panel = GoomePanel(project, testRootDisposable)
        panel.setFiles(panel.findFiles())
        val items = panel.items()
        assertEquals(listOf("Quick actions", "Builder files", "Templates", "Snippets"), items.map { it.label })
        assertEquals(listOf("midnight.goome-theme.json", "demo.goome.json"), items[1].children.map { it.label })
        assertTrue(panel.tree.rowCount > 10)
        panel.run(items[1].children.single { it.label == "demo.goome.json" })
        assertEquals(demo, com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedFiles.firstOrNull())
    }

    fun testSnippetFromToolWindow() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        myFixture.configureByText("A.java", "class A { void f() { <caret> } }")
        val panel = GoomePanel(project, testRootDisposable)
        assertEquals("java", panel.lang)
        val button = panel.items()[3].children.single { it.label == "goome-button" }
        assertTrue(panel.insertSnippet(button.arg, button.arg2))
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("ui.button("))
        myFixture.configureByText("x.txt", "")
        assertTrue(panel.insertSnippet("csharp", "goome-button"))
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("ui.Button("))
        assertFalse(myFixture.editor.document.text.contains("\${"))
    }
}
