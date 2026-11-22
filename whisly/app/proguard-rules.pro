# Whisly Production R8 / ProGuard Keep Rules

# Preserve source file and line numbers in stack traces for crash diagnosis
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep ViewModels and AndroidViewModels for reflective instantiation by ViewModelProvider
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}
-keep class com.example.presentation.WhislyViewModel {
    <init>(...);
    public <methods>;
}

# Keep Safe Mode crash protection and Application entry point
-keep class com.example.WhislyApp { *; }
-keep class com.example.core.crash.** { *; }

# Keep WorkManager Workers and Download Foreground Service
-keep class * extends androidx.work.ListenableWorker {
    <init>(...);
}
-keep class com.example.ai.modelmanager.WhislyDownloadService { *; }

# Keep JNI native methods and classes with native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

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

# Keep Google ML Kit Translate & On-Device Engine
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep Kotlin Coroutines & Flow internals
-dontwarn kotlinx.coroutines.**
