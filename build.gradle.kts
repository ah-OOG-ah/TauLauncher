plugins {
    id("org.taumc.gradle.versioning")
}

group = "org.taumc.launcher"
project.version = tau.versioning.version(rootProject.properties["project_version"].toString(), rootProject.properties["release_channel"])

subprojects {
    group = "org.taumc.launcher"
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

tasks.register("showCommitChangelog") {
    doLast {
        println(tau.versioning.commitChangeLog)
    }
}