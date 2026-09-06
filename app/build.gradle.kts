plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.disastermesh.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.disastermesh.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1-milestone1"
    }

    buildTypes {
        release {
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Icon set for the bottom navigation and category chips (versioned by the Compose BOM).
    implementation(libs.androidx.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)

    // Google Nearby Connections — the offline device-to-device transport.
    implementation(libs.play.services.nearby)
}
