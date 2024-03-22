plugins {
    kotlin("jvm") version "1.9.23"
    id("com.google.devtools.ksp") version "1.9.23-1.0.19"
}


group = "com.mixfa"
version = "1.0-SNAPSHOT"


repositories {
    mavenCentral()
}
dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))


    implementation("io.arrow-kt:arrow-core:1.2.1")
    implementation("io.arrow-kt:arrow-fx-coroutines:1.2.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    implementation(project(":excify_core"))
    ksp(project(":excify_core"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
