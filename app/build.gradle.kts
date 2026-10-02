
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
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

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            val storePath =
                keystoreProperties.getProperty("storeFile")

            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
            }

            storePassword =
                keystoreProperties.getProperty("storePassword")

            keyAlias =
                keystoreProperties.getProperty("keyAlias")

            keyPassword =
                keystoreProperties.getProperty("keyPassword")
        }
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            // Only apply signing config if keystore.properties exists and has all required values
            val storeFile = keystoreProperties.getProperty("storeFile")?.trim()
            val storePassword = keystoreProperties.getProperty("storePassword")?.trim()
            val keyAlias = keystoreProperties.getProperty("keyAlias")?.trim()  
            val keyPassword = keystoreProperties.getProperty("keyPassword")?.trim()
            
            if (!storeFile.isNullOrBlank() && !storePassword.isNullOrBlank() && 
                !keyAlias.isNullOrBlank() && !keyPassword.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }

        resources {
            excludes += setOf("META-INF/DEPENDENCIES")
        }
    }
}

// sherpa-onnx is published through JitPack.
// Its AAR includes the arm64-v8a native libraries.

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
    testImplementation("org.json:json:20231013")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}