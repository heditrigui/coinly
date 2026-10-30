plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.coinly.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.coinly.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so the release APK can be sideloaded directly.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // App lock (fingerprint, face or the phone's PIN). Its dialogs need an AppCompat theme on Android 8.
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Daily bill reminders and widget refresh. Works offline; adds no internet permission.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // The real org.json, since Android's copy is only a stub in local unit tests.
    testImplementation("org.json:json:20240303")
}
