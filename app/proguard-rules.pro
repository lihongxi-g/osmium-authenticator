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

# ---- keyattestation (vendored Google verifier; see keyattestation/NOTICE) ----
# The verifier registers a JCA CertPathValidator provider and parses ASN.1
# through BouncyCastle; keep the vendored package so the provider wiring and
# the constraint/challenge plumbing survive obfuscation and shrinking.
-keep class com.android.keyattestation.verifier.** { *; }
# Missing-class suppressions for JVM-only corners of the crypto stack
# (never used on Android, referenced from optional integration code).
-dontwarn javax.naming.**
-dontwarn java.awt.**
-dontwarn org.bouncycastle.**

# ---- Argon2id (backup encryption levels) ----
# BouncyCastle's Argon2 generator is called directly, so R8 keeps it anyway;
# the explicit rule guards against future restructuring of the class and is
# cheap (a handful of classes, no reflection inside).
-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters$* { *; }
