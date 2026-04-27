plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lumos.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lumos.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.7"
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(project(":core-designsystem"))
    implementation(project(":feature-onboarding"))
    implementation(project(":feature-profile"))
    implementation(project(":feature-discovery"))
    implementation(project(":feature-requests"))
    implementation(project(":feature-chat"))
    implementation(project(":feature-safety"))
    implementation(project(":feature-diagnostics"))
}
