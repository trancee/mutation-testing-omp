plugins {
    kotlin("jvm") version "2.4.0"
    id("io.github.anschnapp.mutflow") version "1.1.0"
}

apply(from = rootProject.file("../.omp/mutation-results.gradle.kts"))

kotlin {
    sourceSets {
        val main by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        val test by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.github.anschnapp.mutflow:mutflow-junit6:1.1.0")
                implementation("io.github.anschnapp.mutflow:mutflow-annotations:1.1.0")
                implementation("org.junit.jupiter:junit-jupiter:6.0.0")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}
