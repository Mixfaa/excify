plugins {
    kotlin("jvm") version "1.9.23"
}

group = "com.mixfa"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    implementation("io.arrow-kt:arrow-core:1.2.1")
    implementation("io.arrow-kt:arrow-fx-coroutines:1.2.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}