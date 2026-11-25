import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
}
android {
    namespace = "jp.tsuzutore.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "jp.tsuzutore.app"
        minSdk = 26
        targetSdk = 36
        // Raise on every release so an older APK can no longer be installed over newer data by accident.
        versionCode = 4
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    signingConfigs.getByName("debug").storeFile = file("../.tooling/debug.keystore")
    buildTypes {
        // Everyday build: not debuggable, so Compose runs at full speed. It is signed with the same
        // local key as debug, so it updates the installed app in place and keeps its data.
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint { abortOnError = true; checkReleaseBuilds = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
// Keep the local debug signing key out of the source archive and the user's global profile.
val debugKeyFile = rootProject.file(".tooling/debug.keystore")
val prepareLocalDebugKey = tasks.register("prepareLocalDebugKey") {
    outputs.file(debugKeyFile)
    doLast {
        if (!debugKeyFile.exists()) {
            debugKeyFile.parentFile.mkdirs()
            val keytool = File(System.getProperty("java.home"), "bin/keytool" + if (System.getProperty("os.name").startsWith("Windows")) ".exe" else "")
            val result = ProcessBuilder(keytool.absolutePath, "-genkeypair", "-keystore", debugKeyFile.absolutePath,
                "-storepass", "android", "-alias", "androiddebugkey", "-keypass", "android", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "10000", "-dname", "CN=Android Debug,O=Android,C=US", "-storetype", "JKS")
                .inheritIO().start().waitFor()
            check(result == 0) { "ローカルのdebug署名鍵を生成できませんでした。" }
            logger.warn("新しい署名鍵を作成しました: ${debugKeyFile.absolutePath}\n" +
                "以前の鍵で入れたアプリには上書きできません。この鍵は別の場所にもバックアップしてください。")
        }
    }
}
tasks.matching { it.name == "validateSigningDebug" || it.name == "validateSigningRelease" }.configureEach { dependsOn(prepareLocalDebugKey) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.11.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
