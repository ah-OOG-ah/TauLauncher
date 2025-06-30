plugins {
    id("org.taumc.gradle.versioning")
}

group = "org.taumc"
project.version = tau.versioning.version(rootProject.properties["project_version"].toString(), rootProject.properties["release_channel"])

subprojects {
    project.version = rootProject.version
}

tasks.register("showReleaseTag") {
    doLast {
        println(tau.versioning.releaseTag)
    }
}

tasks.register("showVersion") {
    doLast {
        println(tau.versioning.version)
    }
}