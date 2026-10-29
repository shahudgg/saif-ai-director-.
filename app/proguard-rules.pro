# R8 / ProGuard Configuration for SAIF AI

# Room persistence library
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.Entity
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.Dao
-keep @androidx.room.Dao class * { *; }

# Moshi JSON serialization
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
}
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keep class * extends com.squareup.moshi.JsonAdapter { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn org.codehaus.mojo.animal_sniffer.*

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Keep JavaScript Interface methods for WebView Runner Bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Android KeyStore and Cryptography
-keepclassmembers class javax.crypto.** { *; }
-keepclassmembers class java.security.** { *; }

# Data Models
-keep class com.example.llm.** { *; }
-keep class com.example.data.local.** { *; }
-keep class com.example.data.remote.** { *; }
-keep class com.example.agent.** { *; }

# Retain source lines for crash reporting in Diagnostics
-keepattributes SourceFile,LineNumberTable
