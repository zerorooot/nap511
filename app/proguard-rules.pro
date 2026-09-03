# R8 / ProGuard Configuration Rules for nap511

# Preserve source file and line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# -------------------------------------------------------------------------
# Application Data Beans (Gson & Kotlinx Serialization)
# -------------------------------------------------------------------------
-keep class github.zerorooot.nap511.bean.** { *; }
-keepclassmembers class github.zerorooot.nap511.bean.** { *; }

# Gson rules
-keepattributes AnnotationDefault,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }

# -------------------------------------------------------------------------
# Retrofit & OkHttp
# -------------------------------------------------------------------------
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# -------------------------------------------------------------------------
# Media3 & ExoPlayer & GSYVideoPlayer
# -------------------------------------------------------------------------
-keep class androidx.media3.** { *; }
-keep class tv.danmaku.ijk.media.** { *; }
-keep class com.shuyu.gsyvideoplayer.** { *; }
-dontwarn com.shuyu.gsyvideoplayer.**

# -------------------------------------------------------------------------
# Coil Image Loader
# -------------------------------------------------------------------------
-keep class coil.** { *; }

# -------------------------------------------------------------------------
# Logging (XLog) & ProcessPhoenix
# -------------------------------------------------------------------------
-keep class com.elvishew.xlog.** { *; }
-keep class com.jakewharton.processphoenix.** { *; }

# -------------------------------------------------------------------------
# AndroidX WorkManager
# -------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class github.zerorooot.nap511.worker.** { *; }
