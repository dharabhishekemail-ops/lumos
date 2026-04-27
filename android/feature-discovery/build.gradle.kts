plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
  namespace = "com.lumos.discovery"
  compileSdk = 35
  defaultConfig { minSdk = 26 }
  buildFeatures { compose = true }
  composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
}
dependencies {
  implementation(platform("androidx.compose:compose-bom:2025.01.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation(project(":core-designsystem"))
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
}
