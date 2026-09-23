plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.safekey.authenticator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.safekey.authenticator"
        minSdk = 26
        targetSdk = 34
        versionCode = 64
        versionName = "2.5.3"
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

    // Per-ABI version codes for the F-Droid build recipe (its convention for
    // split architectures): base * 10 + digit, ordered armeabi-v7a (1) <
    // arm64-v8a (2) < x86_64 (3) so clients pick the best installable APK.
    // Keep the versionCode a plain literal in defaultConfig — the
    // fdroidserver update checker scans the file for it.
    // NOTE: configureEach + a plain for loop on purpose — in .kts files the
    // stdlib Iterable.all((T) -> Boolean) shadows all(Action), and Gradle's
    // DSL wrappers take receiver lambdas (T.() -> Unit), not (T) -> Unit.
    val baseVersionCode = defaultConfig.versionCode!!
    applicationVariants.configureEach {
        for (output in outputs) {
            val apkOutput = output as? com.android.build.gradle.api.ApkVariantOutput ?: continue
            val digit = when (apkOutput.getFilter(com.android.build.VariantOutput.FilterType.ABI)) {
                "armeabi-v7a" -> 1
                "arm64-v8a" -> 2
                "x86_64" -> 3
                else -> 0
            }
            if (digit != 0) {
                apkOutput.versionCodeOverride = baseVersionCode * 10 + digit
            }
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        // 1.5.14 is the last of the 1.5.x line; matches Kotlin 1.9.24.
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
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
        }
    }
}

dependencies {
    // BOM 2024.09.03 = Compose 1.7.3 (runtime). Upgraded from 2024.01.00
    // (runtime 1.6.0) to fix a ComposerImpl pendingStack underflow crash
    // (IndexOutOfBoundsException in end()/exitGroup) observed on Android 16 /
    // OnePlus during ordinary recomposition — several composer group-stack
    // imbalance bugs were fixed across the 1.7.0–1.7.3 line.
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // NOTE: material-icons-core resolves to an empty shell AAR via BOM 2024.01
    // (icons moved to the -android variant in 1.6.0). All icons are self-drawn
    // in AppIcons.kt from official Material Symbols path data instead.

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // QR generation + scanning for account sharing / imports (core only, no camera bloat)
    implementation("com.google.zxing:core:3.5.2")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Scheduled auto-backup (WebDAV / local storage). Google first-party;
    // handles Doze, process death and reboot without custom alarm plumbing.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Android 14/15 BiometricPrompt fixes. 1.1.0/1.2.0-alpha05 misbehave on
    // ColorOS 15; 1.4.0-alpha02 requires compileSdk 35 (a real release SDK).
    implementation("androidx.biometric:biometric:1.4.0-alpha02")

    // 1.4.x is the first line whose native libraries are 16 KB page-size aligned
    // (1.3.x ships a 4 KB-aligned libimage_processing_util_jni.so, which Android
    // flags as incompatible with 16 KB devices — Android 15+/17 hardware).
    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Vendored Google Android Key Attestation verifier (see keyattestation/NOTICE).
    implementation(project(":keyattestation"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // XmlPullParser on the JVM for unit-testing the WebDAV multistatus parser
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// The WebDAV client sets PROPFIND/MKCOL via reflection on the JDK
// HttpURLConnection 'method' field (fine on Android, no module system).
// On the JVM test host the java.net package is not opened — allow it there.
tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens", "java.base/java.net=ALL-UNNAMED")
}
