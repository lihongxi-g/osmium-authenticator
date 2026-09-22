plugins {
    // 8.5.1+ is the AGP line that aligns uncompressed shared libraries to 16 KB
    // inside the APK (Android's 16 KB page-size requirement, needed on newer
    // devices). Kotlin/KSP/Compose versions below are unchanged and compatible.
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
