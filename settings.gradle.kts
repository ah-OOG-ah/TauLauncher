dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.taumc.org/releases")
    }

    plugins {
        operator fun String.invoke(): String = extra[this] as? String ?: error("Property $this not found")

        id("com.github.johnrengelman.shadow") version "7.1.2"
        id("io.github.gradle-nexus.publish-plugin") version "1.1.0"

        id("org.taumc.gradle.versioning") version("taugradle_version"())
        id("org.taumc.gradle.publishing") version("taugradle_version"())
    }
}

rootProject.name = "TauLauncher"

include("launcher-core")
include("launcher-cli")
include("launcher-gui")
