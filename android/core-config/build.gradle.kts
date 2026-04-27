plugins { kotlin("jvm"); kotlin("plugin.serialization") }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core-common"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
