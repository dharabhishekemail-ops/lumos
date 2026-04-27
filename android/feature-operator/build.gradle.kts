plugins { id("com.android.library"); kotlin("android") }
android { namespace="com.lumos.feature.operator"; compileSdk=34; defaultConfig{ minSdk=26 } }
dependencies {
  implementation("androidx.compose.ui:ui:1.6.8")
  implementation("androidx.compose.material3:material3:1.2.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
