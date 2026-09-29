import java.util.Properties

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
    // 34.0.0 isn't installed and there's no sdkmanager to fetch it; use an installed one.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.warivo.os"
        minSdk = 28          // Android 9
        targetSdk = 29       // Android 10
        versionCode = 1
        versionName = "0.1.0"

        // Google Maps key. Kept out of git: put MAPS_API_KEY=... in local.properties.
        // The map only renders where Google Play Services is present (a normal phone),
        // not on the GApps-free Warivo ROM — see os/README.md.
        val mapsKey = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }.getProperty("MAPS_API_KEY", "")
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
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
    // Transitive via material3, but declared: the boot and lock screens are the
    // first thing in the app that actually animates.
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Google Maps (Maps Compose). Needs Google Play Services on the device and a Maps
    // API key (local.properties → MAPS_API_KEY); it shows a blank map without either.
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.maps.android:maps-compose:6.1.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
