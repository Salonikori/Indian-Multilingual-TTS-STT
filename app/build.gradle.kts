plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.itantra.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.itantra.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0-smoke"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildFeatures { compose = true }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        jniLibs { useLegacyPackaging = false }
        // Keep license/notice files in the APK for open-source attribution.
        resources.excludes += setOf("META-INF/DEPENDENCIES")
    }
}
// sherpa-onnx is published to JitPack from the official k2-fsa/sherpa-onnx repository.
// The JitPack repository is declared in settings.gradle.kts.
// The AAR bundles arm64-v8a native .so files, so no separate jniLibs/ copy is needed.
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
