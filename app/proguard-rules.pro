# Retrofit 2 rules
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses

# Gson rules
-keep class com.google.gson.** { *; }
-keep class com.example.ektachildhospital.api.** { *; }
-keepattributes *Annotation*

# OkHttp 3 rules
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**

# DataStore rules
-keep class androidx.datastore.** { *; }