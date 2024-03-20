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

    implementation(project(":excify_core"))
    ksp(project(":excify_core"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
