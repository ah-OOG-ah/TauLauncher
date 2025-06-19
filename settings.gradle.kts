dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

pluginManagement {
    plugins {
        id("com.github.johnrengelman.shadow") version "7.1.2"
        id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    }
}

rootProject.name = "TauLauncher"

include("launcher-core")
include("launcher-cli")
include("launcher-gui")
