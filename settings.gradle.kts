pluginManagement {
    repositories {
        mavenLocal()
        maven {
            url = uri("https://maven.aliyun.com/repository/public/")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/gradle-plugin")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("https://www.jetbrains.com/intellij-repository/releases")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies")
            isAllowInsecureProtocol = true
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "juice-plugin"
