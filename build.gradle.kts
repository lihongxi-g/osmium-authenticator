plugins {
    // Toolchain as of the 2026-09 upgrade: AGP 8.7.x (16 KB page-size alignment,
    // compileSdk 35), Kotlin 2.0 with the Compose compiler plugin (Kotlin 2 moved
    // the compiler out of composeOptions), and the matching KSP.
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.25" apply false
}
