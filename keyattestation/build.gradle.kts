/*
 * Vendored copy of https://github.com/android/keyattestation (Apache-2.0).
 * Pinned at commit 100dedaea2387d9705d29b2be3177466c33ae17b; see NOTICE for
 * the full list of modifications applied in this copy.
 *
 * Build script rewritten for the Osmium project: upstream targets Kotlin 2.2.0
 * with a Java 21 toolchain, while this project stays on Kotlin 1.9.24 / JVM 17.
 * Dependencies mirror upstream versions; guava is switched to the -android
 * variant and coroutines are pinned to the version already used by the app.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}

dependencies {
    // The public API of the verifier references guava (ListenableFuture) and
    // protobuf (ByteString) types, so they are exposed as `api` for consumers.
    api("com.google.guava:guava:33.5.0-android")
    api("com.google.protobuf:protobuf-javalite:4.28.3")
    api("com.google.protobuf:protobuf-kotlin-lite:4.28.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    implementation("androidx.annotation:annotation:1.9.1")
    implementation("co.nstant.in:cbor:0.9")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.errorprone:error_prone_annotations:2.41.0")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.testparameterinjector:test-parameter-injector:1.18")
    testImplementation("com.google.truth:truth:1.4.4")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
}

// roots.json lives at the module root and is used in two ways: the upstream
// test suite reads it from the filesystem, and GoogleTrustAnchors loads it
// from the classpath at runtime. processResources publishes the single copy.
tasks.processResources {
    from("roots.json")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions { jvmTarget = "17" }
}

// Upstream enables `javaParameters` for the test compilation (used by
// test-parameter-injector's reflection).
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    kotlinOptions { javaParameters = true }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
