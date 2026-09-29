import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val releaseSecrets = Properties().apply {
    val file = rootProject.file(".signing/release.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
android {
    namespace = "me.chile.app"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "me.chile.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 40
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    sourceSets.getByName("main").resources.srcDir("../shared")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        if (releaseSecrets.isNotEmpty()) create("release") {
            storeFile = rootProject.file(".signing/chileme-release.jks")
            storePassword = releaseSecrets.getProperty("storePassword")
            keyAlias = "chileme"
            keyPassword = releaseSecrets.getProperty("keyPassword")
        }
    }
    buildTypes { release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        isDebuggable = false
        if (releaseSecrets.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
    } }
}
dependencies {
    implementation("org.commonmark:commonmark:0.24.0")
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
