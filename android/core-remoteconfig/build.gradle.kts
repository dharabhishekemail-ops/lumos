plugins {
    id("com.android.library")
    kotlin("android")
}
android {
    namespace = "com.lumos.core.remoteconfig"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
