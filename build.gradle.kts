plugins {
    id("org.taumc.gradle.versioning")
    id("org.taumc.gradle.publishing")
}

group = "org.taumc"
project.version = tau.versioning.version(rootProject.properties["project_version"].toString(), rootProject.properties["release_channel"])

subprojects {
    project.version = rootProject.version
}

val tauGradlePublish = tau.publishing.publish {
    useTauGradleVersioning()

    changelog = tau.versioning.commitChangeLog

    github("GitHub") {
        supportAllChannels()
        uploadArtifacts = false

        accessToken = System.getenv("GITHUB_TOKEN")
        repository = "TauMC/TauLauncher"
        tagName = tau.versioning.releaseTag
    }
}