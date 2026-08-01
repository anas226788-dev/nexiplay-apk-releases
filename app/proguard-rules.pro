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
