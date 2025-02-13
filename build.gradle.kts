plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.1.10"
    `maven-publish`
    id("org.jetbrains.kotlinx.dataframe") version "0.15.0"
}

group = "net.nashat"
version = "1.0"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.jlinalg:jlinalg:0.9.1")
    implementation("org.jetbrains.kotlinx:dataframe:0.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "potuz-pubsub"
        }
    }
}