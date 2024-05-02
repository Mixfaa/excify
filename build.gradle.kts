plugins {
    kotlin("jvm") version "1.9.23"
    id("com.google.devtools.ksp") version "1.9.23-1.0.19"
    id("maven-publish")
}

group = "com.github.Mixfaa"
version = "0.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

//    implementation("io.arrow-kt:arrow-core:1.2.1")

    implementation("com.squareup:kotlinpoet:1.16.0")
    implementation("com.squareup:kotlinpoet-ksp:1.16.0")

    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.23-1.0.19")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
}

publishing {

    publications {
        register<MavenPublication>("excify") {
            from(components["kotlin"])
        }
    }
}

java {
    version = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}