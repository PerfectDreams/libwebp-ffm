plugins {
    kotlin("jvm") version "2.3.10"
    `maven-publish`
}

group = "net.perfectdreams.libwebpffm"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            name = "PerfectDreams"
            url = uri("https://repo.perfectdreams.net/")
            credentials(PasswordCredentials::class)
        }
    }
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}