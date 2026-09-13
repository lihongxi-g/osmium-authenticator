# Project specific ProGuard/R8 rules for Osmium.

# ---- kotlinx.serialization (vault export/import file format) ----
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.safekey.authenticator.**$$serializer { *; }
-keepclassmembers class com.safekey.authenticator.** {
    *** Companion;
}
-keepclasseswithmembers class com.safekey.authenticator.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- zxing core (QR generation for account sharing) ----
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }

# ---- [TEMP: S3 size probe — remove in S4 when real usage lands] ----
# Keep the vendored key-attestation verifier reachable so the release build
# measures its worst-case contribution to APK size.
-keep class com.android.keyattestation.verifier.** { *; }
# Missing-class suppressions for the JVM-only corners of the crypto stack.
-dontwarn javax.naming.**
-dontwarn java.awt.**
-dontwarn org.bouncycastle.**
