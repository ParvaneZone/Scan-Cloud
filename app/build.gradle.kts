plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.cfscanner"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.cfscanner"
        minSdk = 24
        targetSdk = 34
        versionCode = (project.findProperty("appCode") as String?)?.toIntOrNull() ?: 3
        versionName = (project.findProperty("appVersion") as String?) ?: "3.0"
    }

    signingConfigs {
        getByName("debug") {
            if (file("debug.keystore").exists()) {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    splits {
        abi {
            isEnable = false
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
