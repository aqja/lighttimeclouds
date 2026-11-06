import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

// The sky tables are computed from SkyModel at build time instead of living in the source tree
// as binaries (F-Droid builds everything from source). Reruns only when the code changes.
val generateSkyTable = tasks.register<JavaExec>("generateSkyTable") {
    description = "Computes the sky table resources from SkyModel."
    val outputDir = layout.buildDirectory.dir("generated/skyTable")
    classpath = sourceSets.main.get().output.classesDirs + configurations.runtimeClasspath.get()
    mainClass.set("com.umfluten.lighttimeclouds.sky.SkyTableGeneratorKt")
    outputs.dir(outputDir)
    argumentProviders.add(CommandLineArgumentProvider { listOf(outputDir.get().asFile.absolutePath) })
}

sourceSets.main {
    resources.srcDir(generateSkyTable)
}
