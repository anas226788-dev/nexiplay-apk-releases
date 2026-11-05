# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# Supabase / Ktor
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlin Serialization
-keepclassmembers class com.nexiplay.app.data.model.** { *; }
-keep class com.nexiplay.app.data.model.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }

# Coil
-keep class coil3.** { *; }

# ExoPlayer
-keep class androidx.media3.** { *; }

# ── Ad SDKs (CRITICAL - Without these, R8 strips ad classes in release builds) ──

# Start.io SDK
-keep class com.startapp.** { *; }
-dontwarn com.startapp.**
-keepattributes Exceptions, InnerClasses

# Unity LevelPlay / IronSource SDK & Unity Ads
-keep class com.ironsource.** { *; }
-dontwarn com.ironsource.**
-keep class com.unity3d.** { *; }
-dontwarn com.unity3d.**
-keep class com.unity3d.services.** { *; }
-dontwarn com.unity3d.services.**
-keep class com.unity3d.ads.** { *; }
-dontwarn com.unity3d.ads.**
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Google Play Services (required by both ad SDKs)
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# AdManager data class
-keep class com.nexiplay.app.data.util.AdSettings { *; }
-keep class com.nexiplay.app.data.util.AdManager { *; }

