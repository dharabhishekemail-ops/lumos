plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
