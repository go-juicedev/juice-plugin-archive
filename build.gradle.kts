plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.16.1"
}

group = "com.github.eatmoreapple"
version = "1.4-SNAPSHOT"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin
intellij {
    version.set("2023.1") // 指定 IntelliJ IDEA 版本
    type.set("GO")
    plugins.set(listOf(
        "com.intellij.database",
        "org.intellij.intelliLang",  // Add SQL language dependency
        "org.jetbrains.plugins.go"  // Add Go plugin dependency
    ))
    downloadSources.set(false)
    updateSinceUntilBuild.set(false)
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }

    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        sinceBuild.set("241")  // 最低支持的 IDE 版本
        version.set(project.version.toString())
    }
}
