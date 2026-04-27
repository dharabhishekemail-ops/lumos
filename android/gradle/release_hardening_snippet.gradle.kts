// Release hardening template snippet (app/build.gradle.kts)

android {
  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      // Ensure logs are stripped / gated
      buildConfigField("Boolean", "LOGGING_ENABLED", "false")
    }
    getByName("debug") {
      buildConfigField("Boolean", "LOGGING_ENABLED", "true")
    }
  }

  packagingOptions {
    resources.excludes += setOf("META-INF/*.kotlin_module")
  }
}
