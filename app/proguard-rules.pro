# Whisly Production R8 / ProGuard Keep Rules

# Preserve source file and line numbers in stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Room Database, Entities, and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.example.data.local.** { *; }

# Keep Retrofit API interfaces and Moshi DTOs
-keep interface com.example.data.remote.** { *; }
-keep class com.example.data.remote.** { *; }
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# Keep AndroidX SplashScreen compatibility layer
-keep class androidx.core.splashscreen.** { *; }

# Keep Kotlin Coroutines & Flow internals
-dontwarn kotlinx.coroutines.**
