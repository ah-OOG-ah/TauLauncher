plugins {
    id("application")
    id("org.beryx.runtime") version "1.12.5"
}

dependencies {
    implementation(project(":launcher-core"))
    implementation("com.formdev:flatlaf:3.6")
    implementation("org.kordamp.ikonli:ikonli-swing:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-fontawesome6-pack:12.4.0")

    implementation("com.miglayout:miglayout-swing:11.4.2")

    // Image support
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    implementation("com.formdev:flatlaf-extras:3.6")

    implementation("org.apache.logging.log4j:log4j-api:2.20.0")     // Log4j2 API
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")    // Log4j2 Core impl
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0") // SLF4J to Log4j2 bridge
}

val generateVersionFile by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/version")
    inputs.property("version", project.version)  // Declare version as input
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("taulauncher_version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=${project.version}")
    }
}

sourceSets.main {
    resources.srcDir(generateVersionFile.map { it.outputs.files.singleFile })
}

application {
    mainClass = "org.taumc.launcher.gui.TauLauncherEntryPoint"
    applicationName = "TauLauncher"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dtau.launcher.production=true", "-Xmx256m")
}

tasks.named<JavaExec>("run") {
    val runDir = rootProject.projectDir.resolve("run")
    doFirst {
        runDir.mkdirs()
    }
    workingDir = runDir
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
    from("3RD-PARTY-LICENSES")
}

runtime {
    modules = listOf(
        "java.base",
        "java.rmi",
        "java.desktop",
        "jdk.crypto.ec",
        "jdk.crypto.cryptoki",
        "java.security.sasl",
        "java.net.http",
        "java.naming",
        "java.security.jgss"
    )
    options = listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages")

    jpackage {
        if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            installerOptions.addAll(listOf("--win-per-user-install", "--win-dir-chooser", "--win-menu", "--win-shortcut"))
        }
    }
}

/*
tasks.named<JPackageTask>("jpackage") {
    dependsOn(tasks.named("shadowJar"))
    input = "${layout.buildDirectory.get()}/libs"
    destination = "${layout.buildDirectory.get()}/dist"
    removeDestination = true

    appName = "TauLauncher"
    vendor = "taumc.org"

    mainJar = tasks.shadowJar.get().archiveFileName.get()
    mainClass = "org.taumc.launcher.gui.Main"

    javaOptions = listOf("-Dfile.encoding=UTF-8", "-Dtau.launcher.production=true", "-Xmx256m")
    val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
    type = when {
        osName.contains("windows") -> ImageType.EXE
        osName.contains("mac") -> ImageType.DMG
        osName.contains("linux") -> ImageType.APP_IMAGE
        else -> throw GradleException("Unsupported OS: $osName")
    }
}

 */