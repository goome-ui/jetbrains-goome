package io.github.goomeui.goome

import com.intellij.json.JsonFileType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import javax.swing.Icon

object Goome {
    const val DOCS_URL = "https://goome.wtf/docs/"
    const val BUILDER_URL = "https://goome.wtf/builder/"
    const val PROJECT_SUFFIX = ".goome.json"
    const val THEME_SUFFIX = ".goome-theme.json"
    val ICON: Icon = IconLoader.getIcon("/icons/goome.svg", Goome::class.java)

    fun isProject(file: VirtualFile?) = file != null && file.name.endsWith(PROJECT_SUFFIX)

    fun say(project: Project?, text: String, type: NotificationType = NotificationType.INFORMATION) {
        NotificationGroupManager.getInstance().getNotificationGroup("goome").createNotification("goome", StringUtil.escapeXmlEntities(text), type).notify(project)
    }
}

class GoomeProjectFileType private constructor() : JsonFileType() {
    override fun getName() = "goome project"
    override fun getDescription() = "goome ui builder project"
    override fun getDisplayName() = "goome project"
    override fun getDefaultExtension() = "goome.json"
    override fun getIcon() = Goome.ICON

    companion object {
        @JvmField
        val INSTANCE = GoomeProjectFileType()
    }
}

class GoomeThemeFileType private constructor() : JsonFileType() {
    override fun getName() = "goome theme"
    override fun getDescription() = "goome theme"
    override fun getDisplayName() = "goome theme"
    override fun getDefaultExtension() = "goome-theme.json"
    override fun getIcon() = Goome.ICON

    companion object {
        @JvmField
        val INSTANCE = GoomeThemeFileType()
    }
}

class GoomeSchemas : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(
        Schema("goome project", Goome.PROJECT_SUFFIX, "/schemas/goome-project.schema.json"),
        Schema("goome theme", Goome.THEME_SUFFIX, "/schemas/goome-theme.schema.json"),
    )

    class Schema(private val label: String, private val suffix: String, private val path: String) : JsonSchemaFileProvider {
        override fun isAvailable(file: VirtualFile) = file.name.endsWith(suffix)
        override fun getName() = label
        override fun getSchemaFile(): VirtualFile? = JsonSchemaProviderFactory.getResourceFile(GoomeSchemas::class.java, path)
        override fun getSchemaType() = SchemaType.embeddedSchema
        override fun getSchemaVersion() = JsonSchemaVersion.SCHEMA_7
        override fun getPresentableName() = label
    }
}

@State(name = "goome", storages = [Storage("goome.xml")])
class GoomeSettings : PersistentStateComponent<GoomeSettings.Data> {
    class Data {
        var cli: String = ""
        var python: String = ""
        var goomeSrc: String = ""
    }

    private var data = Data()

    override fun getState() = data

    override fun loadState(state: Data) {
        data = state
    }

    companion object {
        fun get(): Data = service<GoomeSettings>().state
    }
}

class GoomeConfigurable : BoundConfigurable("goome") {
    override fun createPanel() = panel {
        val s = GoomeSettings.get()
        row("goome or goome.py:") {
            textField().bindText(s::cli).align(AlignX.FILL)
                .comment("Empty = <code>goome</code> on PATH, or else goome.py from the latest goome-cli release")
        }
        row("Python:") {
            textField().bindText(s::python).align(AlignX.FILL)
                .comment("Empty = <code>python3</code>, <code>python</code> or <code>py -3</code>")
        }
        row("c-goome checkout:") {
            textField().bindText(s::goomeSrc).align(AlignX.FILL)
                .comment("Folder of a c-goome checkout, used by Build for Web (<code>goome build --web --goome</code>). Empty = let goome-cli download one")
        }
    }
}
