plugins {
    // Toolchain as of the 2026-09 upgrade: AGP 8.10.x (compileSdk 36, which
    // navigationevent requires, plus 16 KB page-size alignment), Kotlin 2.1 with the
    // Compose compiler plugin (Kotlin 2 moved the compiler out of composeOptions), and
    // the matching KSP. Gradle 8.11.1 is the wrapper AGP 8.10 expects.
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("com.google.devtools.ksp") version "2.1.21-2.0.2" apply false
}
