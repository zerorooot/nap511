# -------------------------------------------------------------------------
# General R8 / ProGuard Optimization & Shrinking Settings
# -------------------------------------------------------------------------
# Preserve line numbers and source file names for crash stack traces
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Allow R8 to widen visibility (private/protected -> public) for aggressive inlining & class merging
-allowaccessmodification

# Repackage obfuscated classes into the root package to shorten class descriptors in DEX
-repackageclasses ''

# -------------------------------------------------------------------------
# Logging Stripping (Release Build Optimization)
# -------------------------------------------------------------------------
# Safely eliminate debug and verbose Android Log calls & parameter string formatting in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# -------------------------------------------------------------------------
# Application Data Beans & Serialization (Gson / Kotlinx Serialization)
# -------------------------------------------------------------------------
# Keep model bean class names, fields, and constructors for reflection-based deserialization
-keep class github.zerorooot.nap511.bean.** {
    <fields>;
    public <init>(...);
}
-keepclassmembers enum github.zerorooot.nap511.bean.** { *; }

# Standard Gson annotations and rules
-keepattributes AnnotationDefault,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers enum * { *; }
-dontwarn com.google.gson.**
-dontwarn sun.misc.**

# -------------------------------------------------------------------------
# Network: Retrofit & OkHttp
# -------------------------------------------------------------------------
# Retrofit and OkHttp include consumer rules in their AARs (retrofit2.pro / okhttp-android).
# Suppress optional runtime dependency warnings.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# -------------------------------------------------------------------------
# Media & Video Player: GSYVideoPlayer, ijkplayer & Media3 / ExoPlayer
# -------------------------------------------------------------------------
# 1) ijkplayer: C/C++ native layer reflects Java player callbacks
-keep class tv.danmaku.ijk.media.player.** { *; }
-dontwarn tv.danmaku.ijk.media.player.**

# 2) ExoPlayer bridge in GSYVideoPlayer
-keep class tv.danmaku.ijk.media.exo2.** { *; }
-dontwarn tv.danmaku.ijk.media.exo2.**

# 3) GSYVideoPlayer reflection points (constructors & player/cache managers)
-keep class * extends com.shuyu.gsyvideoplayer.video.base.GSYBaseVideoPlayer {
    public <init>(android.content.Context);
    public <init>(android.content.Context, java.lang.Boolean);
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keep class * implements com.shuyu.gsyvideoplayer.player.IPlayerManager {
    public <init>();
}
-keep interface com.shuyu.gsyvideoplayer.player.IPlayerManager { *; }

-keep class * implements com.shuyu.gsyvideoplayer.cache.ICacheManager {
    public <init>();
}
-keep interface com.shuyu.gsyvideoplayer.cache.ICacheManager { *; }

# Media3 includes fine-grained consumer rules. Suppress warnings from optional extensions.
-dontwarn androidx.media3.**
-dontwarn com.google.android.exoplayer2.**
-dontwarn com.shuyu.gsyvideoplayer.**

# -------------------------------------------------------------------------
# Logging (XLog)
# -------------------------------------------------------------------------
-dontwarn com.elvishew.xlog.**

# -------------------------------------------------------------------------
# AndroidX WorkManager
# -------------------------------------------------------------------------
# Keep Worker reflection constructors (automatically covers CoroutineWorker subclasses)
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
