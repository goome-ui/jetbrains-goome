import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.goomeui"
version = providers.gradleProperty("pluginVersion").get()

val pluginId = "io.github.goomeui.goome"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        bundledPlugins("com.intellij.java", "com.intellij.modules.json")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        freeCompilerArgs.add("-Xjdk-release=21")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

val generated = layout.buildDirectory.dir("generated/goome")

val syncGoome = tasks.register("syncGoome") {
    val root = projectDir
    val out = generated.get().asFile
    inputs.dir("snippets")
    inputs.dir("schemas")
    listOf("GOOME_DOCS", "GOOME_C").forEach { inputs.property(it, System.getenv(it) ?: "") }
    outputs.dir(out)
    outputs.upToDateWhen { false }
    doLast {
        GoomeSync.run(root, out, pluginId)
    }
}

sourceSets {
    main {
        resources.srcDir(syncGoome)
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
    pluginConfiguration {
        id = pluginId
        version = project.version.toString()
        ideaVersion {
            sinceBuild = providers.gradleProperty("sinceBuild")
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            val want = providers.gradleProperty("verifyIde").orNull
            if (want != null) {
                val (type, ver) = want.split(":")
                create(IntelliJPlatformType.fromCode(type), ver)
            }
        }
    }
}

tasks.test {
    maxHeapSize = "1g"
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    systemProperty("goome.root", projectDir.absolutePath)
}
