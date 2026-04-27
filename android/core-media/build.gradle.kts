plugins { id("com.android.library"); kotlin("android") }
android { namespace="com.lumos.core.media"; compileSdk=34; defaultConfig { minSdk=26 } }
dependencies {
  implementation(project(":core-crypto-api"))
  implementation(project(":core-protocol"))
  testImplementation(kotlin("test"))
}
