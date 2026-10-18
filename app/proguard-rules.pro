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
# (涵盖 bean.sync 局域网同步实体、bean.cache 磁盘缓存实体等全部业务 Bean)
#noinspection ProguardRuleBroadKeep
-keep class github.zerorooot.nap511.bean.** {
    <fields>;
    public <init>(...);
}
-keepclassmembers enum github.zerorooot.nap511.bean.** { *; }

# Keep worker tasks reflection / serialization models (后台解压缩任务持久化)
-keep class github.zerorooot.nap511.worker.UnzipTaskItem {
    <fields>;
    public <init>(...);
}

# Standard Gson annotations and rules
-keepattributes AnnotationDefault,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers enum * { *; }
-dontwarn com.google.gson.**
-dontwarn sun.misc.**

# Kotlinx Serialization（适配 Jetpack Compose Navigation 3 类型安全路由序列化）
-dontwarn kotlinx.serialization.**
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    public static ** INSTANCE;
}

# -------------------------------------------------------------------------
# Network: Retrofit & OkHttp (含 OkHttp 5.x 及嵌入式 MockWebServer)
# -------------------------------------------------------------------------
# 1) Retrofit 业务接口定义与方法签名保护
-keep interface github.zerorooot.nap511.service.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# 2) Retrofit and OkHttp include consumer rules in their AARs (retrofit2.pro / okhttp-android).
# Suppress optional runtime dependency warnings (增加对 OkHttp 5.x 核心包 mockwebserver3 的防御性告警抑制).
-dontwarn okhttp3.**
-dontwarn okhttp3.mockwebserver.**
-dontwarn mockwebserver3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# -------------------------------------------------------------------------
# WebView Request Inspector (acsbendi)
# -------------------------------------------------------------------------
# 该库 AAR 未自带 Consumer 规则，完整保留其公开 API 与 @JavascriptInterface 交互方法
-keep class com.acsbendi.requestinspectorwebview.** { *; }
-dontwarn com.acsbendi.requestinspectorwebview.**

# -------------------------------------------------------------------------
# CameraX & QR Code (ZXing)
# -------------------------------------------------------------------------
# 抑制相机拓展与二维码分析核心算法库的可选依赖告警
-dontwarn com.google.zxing.**
-dontwarn androidx.camera.**
-dontwarn androidx.camera.viewfinder.**

# -------------------------------------------------------------------------
# Image Loading: Coil
# -------------------------------------------------------------------------
# 抑制图片加载库可能触发的可选解码器告警
-dontwarn coil.**

# -------------------------------------------------------------------------
# Process Phoenix (Application Process Rebirth)
# -------------------------------------------------------------------------
# 保护应用进程冷重启组件
-keep class com.jakewharton.processphoenix.** { *; }

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
# 完整保留 Worker 类名与构造器，防止因类名混淆导致持久化在 SQLite 中的任务在跨版本升级后抛出 ClassNotFoundException
-keep class * extends androidx.work.ListenableWorker { *; }
