plugins {
    id("java-library")
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    api("com.fasterxml.jackson.core:jackson-core:2.19.0")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.19.0")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.0")
    api("com.fasterxml.jackson.core:jackson-annotations:2.19.0")

    api("org.apache.commons:commons-compress:1.26.1")
    api("org.apache.commons:commons-text:1.11.0")
    implementation("commons-codec:commons-codec:1.18.0")
    api("org.apache.commons:commons-exec:1.3")

    api("com.github.mizosoft.methanol:methanol:1.8.2")

    api("net.raphimc:MinecraftAuth:4.1.1")

    implementation("org.apache.maven:maven-artifact:3.9.10")

    api("org.slf4j:slf4j-api:2.0.9")                    // SLF4J API
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")     // Log4j2 API
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")    // Log4j2 Core impl
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0") // SLF4J to Log4j2 bridge

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}