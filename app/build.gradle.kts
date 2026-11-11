plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.safekey.authenticator"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.safekey.authenticator"
        minSdk = 26
        targetSdk = 37
        versionCode = 94
        versionName = "2.6.5.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("safekey") {
            storeFile = file(System.getenv("SAFEKEY_KEYSTORE") ?: "safekey.keystore")
            storePassword = System.getenv("SAFEKEY_STORE_PASS") ?: ""
            keyAlias = System.getenv("SAFEKEY_KEY_ALIAS") ?: "safekey"
            keyPassword = System.getenv("SAFEKEY_KEY_PASS") ?: ""
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            // x86_64 included for Android emulator builds (restored 2026-09-13).
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }


    // The dependency-info block that AGP injects into release APKs is
    // rejected by F-Droid ("found extra signing block") and is the only
    // non-reproducible part of our build output; keep it off.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        debug {
            // Default debug keystore
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // F-Droid rebuilds from the tag and compares byte-for-byte against the
            // published APK. AGP writes the build-time git revision into
            // META-INF/version-control-info.textproto, so the two only match when the
            // release APK happened to be built at the exact commit the tag points to.
            // That string is only used by Android Studio's "jump to commit", so drop it.
            vcsInfo.include = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (file(System.getenv("SAFEKEY_KEYSTORE") ?: "safekey.keystore").exists()) {
                signingConfig = signingConfigs.getByName("safekey")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs.useLegacyPackaging = false
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Vendored crypto jars (bcprov/bcpkix/bcutil) carry per-jar
            // multi-release and JAR-signature metadata that collides when the
            // release resources are merged; Android never reads these entries.
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "/META-INF/versions/11/OSGI-INF/MANIFEST.MF"
            excludes += "/META-INF/versions/15/OSGI-INF/MANIFEST.MF"
            excludes += "/META-INF/versions/21/OSGI-INF/MANIFEST.MF"
            excludes += "/META-INF/versions/9/module-info.class"
            excludes += "/META-INF/BC2048KE.SF"
            excludes += "/META-INF/BC2048KE.DSA"
            // BouncyCastle 1.86 ships its licence as META-INF/LICENSE.md and
            // META-INF/NOTICE.md in all three jars, so the merge sees three
            // copies of the same path and fails. Dropping the jar copies is
            // safe because the licence reaches the APK as
            // assets/licenses/bouncycastle_LICENSE.md and is credited in
            // About → Attributions; without those two, excluding it here
            // would strip the notice MIT requires us to ship.
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/NOTICE.md"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Preserve the existing per-ABI version codes using AGP's public variant API.
androidComponents {
    onVariants { variant ->
        for (output in variant.outputs) {
            val abi = output.filters.firstOrNull {
                it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
            }?.identifier
            val digit = when (abi) {
                "armeabi-v7a" -> 1
                "arm64-v8a" -> 2
                "x86_64" -> 3
                else -> 0
            }
            if (digit != 0) {
                val baseCode = android.defaultConfig.versionCode!!
                output.versionCode.set(baseCode * 10 + digit)
            }
        }
    }
}

dependencies {
    // BOM 2024.09.03 = Compose 1.7.3 (runtime). Upgraded from 2024.01.00
    // (runtime 1.6.0) to fix a ComposerImpl pendingStack underflow crash
    // (IndexOutOfBoundsException in end()/exitGroup) observed on Android 16 /
    // OnePlus during ordinary recomposition — several composer group-stack
    // imbalance bugs were fixed across the 1.7.0–1.7.3 line.
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // NOTE: material-icons-core resolves to an empty shell AAR via BOM 2024.01
    // (icons moved to the -android variant in 1.6.0). All icons are self-drawn
    // in AppIcons.kt from official Material Symbols path data instead.

    // KSP2/Kotlin 2-compatible processor; no schema or migration changes.
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // QR generation + scanning for account sharing / imports (core only, no camera bloat)
    implementation("com.google.zxing:core:3.5.4")
    implementation("androidx.datastore:datastore-preferences:1.3.0")

    // Scheduled auto-backup (WebDAV / local storage). Google first-party;
    // handles Doze, process death and reboot without custom alarm plumbing.
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    // Android 14/15 BiometricPrompt fixes. 1.1.0/1.2.0-alpha05 misbehave on
    // ColorOS 15; 1.4.0-alpha02 requires compileSdk 35 (a real release SDK).
    implementation("androidx.biometric:biometric:1.4.0-beta01")

    // 1.4.x is the first line whose native libraries are 16 KB page-size aligned
    // (1.3.x ships a 4 KB-aligned libimage_processing_util_jni.so, which Android
    // flags as incompatible with 16 KB devices — Android 15+/17 hardware).
    val cameraxVersion = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")

    // Vendored Google Android Key Attestation verifier (see keyattestation/NOTICE).
    implementation(project(":keyattestation"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // XmlPullParser on the JVM for unit-testing the WebDAV multistatus parser
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// The WebDAV client sets PROPFIND/MKCOL via reflection on the JDK
// HttpURLConnection 'method' field (fine on Android, no module system).
// On the JVM test host the java.net package is not opened — allow it there.
tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens", "java.base/java.net=ALL-UNNAMED")
}
