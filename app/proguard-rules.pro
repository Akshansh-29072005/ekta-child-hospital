# Credential Manager
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**

# Google Play Services
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.common.api.** { *; }
-dontwarn com.google.android.gms.**

# Supabase & Ktor
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-keep class io.ktor.client.engine.cio.** { *; }
-dontwarn io.ktor.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-keep class kotlinx.serialization.json.** { *; }
-keep class kotlinx.serialization.internal.** { *; }
-dontwarn kotlinx.serialization.**

# Data Models
-keep class com.example.ektachildhospital.supabase.** { *; }
-keep class com.example.ektachildhospital.api.** { *; }

# General
-keepattributes EnclosingMethod, InnerClasses, Signature
-dontwarn java.lang.management.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn kotlinx.coroutines.debug.AgentInstallationType