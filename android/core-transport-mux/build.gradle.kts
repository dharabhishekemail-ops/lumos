plugins { id("com.android.library"); kotlin("android") }
android { namespace="com.lumos.core.transport.mux"; compileSdk=34; defaultConfig { minSdk=26 } }
dependencies {
  implementation(project(":core-session"))
  testImplementation(kotlin("test"))
}
