# Proguard rules for Torrent Actions
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Kotlinx Serialization models
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.Serializable class *;
}
-keepclassmembers class com.torrentactions.app.data.api.** { *; }
-keepclassmembers class com.torrentactions.app.data.parser.** { *; }
-keepclassmembers enum * { *; }

# Google Tink & Security Crypto
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**
-dontwarn javax.annotation.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
