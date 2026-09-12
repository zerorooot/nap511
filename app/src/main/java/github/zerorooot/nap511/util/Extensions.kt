package github.zerorooot.nap511.util

import android.content.Context
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.memory.MemoryCache
import com.elvishew.xlog.XLog
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

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
