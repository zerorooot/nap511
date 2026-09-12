package github.zerorooot.nap511.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.memory.MemoryCache
import com.elvishew.xlog.XLog
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
/**
 * 将 Throwable 转换为对用户友好的提示文案
 */
fun Throwable.toUserFriendlyMessage(): String {
    return when (this) {
        is SocketTimeoutException -> "网络连接超时，请重试"
        is UnknownHostException -> "无法连接到服务器，请检查网络"
        is HttpException -> "服务器响应异常(${code()})，请稍后重试"
        is IOException -> "网络请求失败: ${localizedMessage ?: "未知网络错误"}"
        else -> localizedMessage ?: message ?: "系统发生未知错误"
    }
}

/**
 * 从当前线程调用栈自动推断调用方的 ClassName.methodName
 *
 * @param defaultTag 无法匹配调用方时的默认 Tag
 * @param ignoredPackages 过滤器中额外要忽略的包路径或类名标识
 */
fun resolveCallerTag(
    defaultTag: String = "AppError",
    ignoredPackages: List<String> = emptyList()
): String {
    val stackTrace = Throwable().stackTrace
    val caller = stackTrace.firstOrNull { element ->
        val cls = element.className
        !cls.startsWith("java.lang.") &&
                !cls.startsWith("kotlin.") &&
                !cls.startsWith("dalvik.system.") &&
                !cls.contains("ExtensionsKt") &&
                !cls.contains("Thread") &&
                ignoredPackages.none { cls.contains(it) }
    } ?: return defaultTag

    val fullClassName = caller.className.substringAfterLast('.')
    // 处理协程/扩展函数/匿名内部类（如 OfflineFileViewModel$refresh$1 -> OfflineFileViewModel）
    val simpleClassName = fullClassName.substringBefore('$').removeSuffix("Kt")
    val methodName = caller.methodName.substringBefore('$')

    return "$simpleClassName.$methodName-$defaultTag"
}

/**
 * 清理 Coil 磁盘缓存中超过 7 天未更新的文件
 */
suspend fun cleanExpiredCoilDiskCache(
    context: Context,
    maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000L
) = withContext(Dispatchers.IO) {
    val cacheDir = context.cacheDir.resolve("thumbnail_cache")
    if (!cacheDir.exists() || !cacheDir.isDirectory) return@withContext

    val now = System.currentTimeMillis()
    cacheDir.listFiles()?.forEach { file ->
        if (file.isFile && (now - file.lastModified() > maxAgeMillis)) {
            file.delete()
        }
    }
}
/**
 * 检查 Coil 的本地缓存状态（MemoryCache 与 DiskCache）
 *
 * @param imageLoader ImageLoader
 * @param key 缓存 Key（如 pickCode）
 * @return 若内存命中或磁盘命中，均返回本地物理缓存文件的绝对路径（如 /data/.../cache/...）；若无缓存则返回 null
 */
@OptIn(ExperimentalCoilApi::class)
fun getCoilCacheUrl(imageLoader: ImageLoader, key: String): String? {
    if (key.isEmpty()) return null

    val isInMemory = imageLoader.memoryCache?.get(MemoryCache.Key(key)) != null
    val localFile = imageLoader.diskCache?.openSnapshot(key)?.use { it.data.toFile() }
    val isOnDisk = localFile != null && localFile.exists()

    // 无论是内存命中还是磁盘命中，只要本地存在物理文件，均返回该文件的绝对路径
    if (isInMemory || isOnDisk) {
        if (localFile != null && localFile.exists()) {
            /**每次访问磁盘缓存时，更新文件的 lastModified 为当前时间，防止被
             *  @see cleanExpiredCoilDiskCache
             *  删除
             */
            localFile.setLastModified(System.currentTimeMillis())
            return localFile.absolutePath
        }
    }

    return null
}

/**
 * Result<T> 扩展：统一在 onFailure 时进行 Log 打印与 Toast 提示
 *
 * @param tag 日志 Tag 标识（为 null 时自动推断调用方 ClassName.methodName）
 * @param customMsg 自定义提示文案（若为空则自动解析异常信息）
 */
fun <T> Result<T>.onFailureToastAndLog(
    tag: String? = null,
    customMsg: String? = null
): Result<T> = onFailure { e ->
    val logTag = tag ?: resolveCallerTag()
    val userMsg = customMsg ?: e.toUserFriendlyMessage()
    XLog.e("[$logTag] $userMsg", e)
    App.instance.toast(userMsg)
}

/**
 * 包装函数：直接执行 Block 并自动在 onFailure 时处理 Log 打印与 Toast 提示
 */
inline fun <T> runCatchingWithToast(
    tag: String? = null,
    customMsg: String? = null,
    block: () -> T
): Result<T> {
    return runCatching(block).onFailureToastAndLog(tag, customMsg)
}

/**
 * 判断允许通知，是否已经授权
 * 返回值为true时，通知栏打开，false未打开。
 */
fun Context.isNotificationEnabled(): Boolean {
    return NotificationManagerCompat.from(this).areNotificationsEnabled()
}

enum class BatteryRestrictionLevel {
    UNRESTRICTED, // 无限制：已忽略电池优化
    OPTIMIZED,    // 优化：默认状态，由系统智能调度
    RESTRICTED    // 受限制：被系统或用户显式限制后台活动
}

/**
 * 判断电池优化，是否已允许后台无限制行为（忽略电池优化）
 * 返回值为true时，已忽略电池优化（允许后台无限制运行），false为未忽略。
 */
fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val batteryRestrictionLevel = getBatteryRestrictionLevel()
    XLog.d("batteryRestrictionLevel $batteryRestrictionLevel")
    return batteryRestrictionLevel == BatteryRestrictionLevel.UNRESTRICTED
}

private fun Context.getBatteryRestrictionLevel(): BatteryRestrictionLevel {
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    // 1. 判断是否处于“无限制”状态（在忽略电池优化白名单中）
    val isIgnoringOptimizations = powerManager?.isIgnoringBatteryOptimizations(packageName) == true
    if (isIgnoringOptimizations) {
        return BatteryRestrictionLevel.UNRESTRICTED
    }

    // 2. 判断是否被显式置于“受限制”状态（Android 9 / API 28+ 支持）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val isRestricted = activityManager?.isBackgroundRestricted == true
        if (isRestricted) {
            return BatteryRestrictionLevel.RESTRICTED
        }
    }

    // 3. 既未加入白名单，也未被显式受限，即处于默认的“优化”档位
    return BatteryRestrictionLevel.OPTIMIZED
}

