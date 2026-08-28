plugins {
    kotlin("jvm") version "2.4.0"
    id("io.github.anschnapp.mutflow") version "1.1.1"
}

apply(from = rootProject.file("../.omp/mutation-results.gradle.kts"))

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenLocal()
    mavenCentral()
}
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testImplementation("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation("io.github.anschnapp.mutflow:mutflow-junit6:1.1.1")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}

mutflow {
    enabled = true
    targets = listOf("example.Calculator")
}
