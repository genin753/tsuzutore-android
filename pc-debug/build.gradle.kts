import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
    id("org.jetbrains.compose") version "1.9.3"
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    sourceSets.main {
        kotlin.srcDir("../app/src/main/java")
        kotlin.include("jp/tsuzutore/app/ui/**", "jp/tsuzutore/app/domain/**", "jp/tsuzutore/app/data/BackupCodec.kt", "jp/tsuzutore/app/desktop/**")
        kotlin.exclude("**/AndroidPlatformUi.kt")
    }
}

// The display font the Android app bundles in res/font, so the PC screens use the same type.
sourceSets.main { resources.srcDir("../app/src/main/res/font") }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test-junit"))
    testImplementation(compose.desktop.uiTestJUnit4)
}

tasks.register("preparePcDebug") {
    dependsOn("classes")
    val runtime = sourceSets.main.get().runtimeClasspath
    val output = layout.buildDirectory.file("pc-runtime-classpath.txt")
    inputs.files(runtime)
    outputs.file(output)
    doLast {
        output.get().asFile.writeText(runtime.files.filter { it.exists() }.joinToString(System.lineSeparator()) { it.absolutePath }, Charsets.UTF_8)
    }
}

tasks.test { systemProperty("java.awt.headless", "false") }
tasks.register("resolvePcDependencies") {
    doLast {
        println("Desktop runtime files: ${configurations.runtimeClasspath.get().files.size}")
        println("Desktop test runtime files: ${configurations.testRuntimeClasspath.get().files.size}")
    }
}