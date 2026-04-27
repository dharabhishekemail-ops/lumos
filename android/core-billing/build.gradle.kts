plugins {
    id("com.android.library")
    kotlin("android")
}
android {
    namespace = "com.lumos.core.billing"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
}
dependencies {
    implementation("com.android.billingclient:billing-ktx:7.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
