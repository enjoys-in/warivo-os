plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.warivo.os"
    // compileSdk must be modern for Compose; targetSdk stays at 29 so we keep the
    // legacy (simpler) Bluetooth + storage permission model on the Android 9/10 phone.
    compileSdk = 34

    defaultConfig {
        applicationId = "com.warivo.os"
        minSdk = 28          // Android 9
        targetSdk = 29       // Android 10
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            // Kept off: MapLibre + reflection-based Device Owner fallbacks are easier
            // to debug unshrunk, and this is a single-device personal build.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Offline-capable OSM map. No Play Services anywhere in this app — the ROM path
    // (Path B) omits GApps, so location comes from LocationManager, not FusedLocation.
    implementation("org.maplibre.gl:android-sdk:11.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
