# Gson models
-keep class com.koora.app.data.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
-dontwarn retrofit2.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
