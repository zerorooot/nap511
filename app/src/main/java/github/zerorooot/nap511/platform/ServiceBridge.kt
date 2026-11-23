package github.zerorooot.nap511.platform

import android.content.Context
import android.content.Intent
import androidx.concurrent.futures.await
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.data.model.FileBean
import github.zerorooot.nap511.data.model.OrderBean
import github.zerorooot.nap511.service.Sha1Service
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.deleteCoilCache
import github.zerorooot.nap511.util.getCoilCacheUrl
import github.zerorooot.nap511.util.isIgnoringBatteryOptimizations
import github.zerorooot.nap511.worker.OfflineTaskWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片及文件缓存清理平台抽象
 * 将 Coil 相关的图片缓存删除与磁盘/内存清理与领域 Module 隔离
 */
interface CoilCacheCleaner {
    /** 删除指定 key 的 Coil 磁盘/内存缓存条目 */
    fun deleteCoilCache(key: String)

    /** 清空 Coil 内存缓存 */
    fun clearMemoryCache()

    /** 清空 Coil 磁盘缓存 */
    fun clearDiskCache()

    /** 优先匹配 Coil 缓存（按 MemoryCache -> DiskCache 顺序短路判断） */
    fun getCachedUrl(key: String): String?

    /**
     * 按「列表缩略图键 fileId + 高清图键 pickCode」双键清理 Coil 内存与磁盘缓存。
     *
     * 键的事实来源：
     * - 列表缩略图：`ui/components/item/FileCellItem.kt` 的 cacheKey = fileId，最终落到
     *   `BaseCellItem` 的 memoryCacheKey / diskCacheKey；
     * - 高清图：`ui/components/item/ImageCellItem.kt` 使用 pickCode 作为请求 key。
     *
     * 全仓唯一实现：FileBrowserModule.cleanFileListCoilCache、FileMutatorModule.removeFile 共用。
     *
     * 【为什么是接口默认实现，而不是顶层扩展函数】
     * 1. 语义归属：这是「Coil 清理器」自身的批量能力，放在契约里调用点无需 import，
     *    也不必去别处找扩展定义；
     * 2. 防静默切换：Kotlin 解析成员优先于扩展，一旦将来有人补一个同名成员，
     *    扩展版会被静默顶掉、编译不报错。写成成员就只有一个实现入口；
     * 3. 测试零负担：默认实现由接口提供，现有全部 Fake（`object : CoilCacheCleaner {}`）
     *    无需补任何方法，只覆盖 4 个抽象成员即可。
     *
     * 【为什么不把 Dispatch 策略外提】IO 边界属于实现细节：调用方只想「清干净」，
     * 不该关心用什么调度器。若将来需要别的调度器，再抽参数而非反向污染调用点。
     */
    suspend fun cleanFileCaches(beans: Collection<FileBean>) {
        // 空集合直接返回，省掉一次 IO 上下文切换
        if (beans.isEmpty()) return
        withContext(Dispatchers.IO) {
            beans.forEach { bean ->
                // 双键都要清：fileId 管列表缩略图，pickCode 管高清图
                if (bean.fileId.isNotEmpty()) deleteCoilCache(bean.fileId)
                if (bean.pickCode.isNotEmpty()) deleteCoilCache(bean.pickCode)
            }
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
class AndroidCoilCacheCleaner(private val context: Context) : CoilCacheCleaner {
    override fun deleteCoilCache(key: String) {
        context.imageLoader.deleteCoilCache(key)
    }

    override fun clearMemoryCache() {
        context.imageLoader.memoryCache?.clear()
    }

    override fun clearDiskCache() {
        context.imageLoader.diskCache?.clear()
    }

    override fun getCachedUrl(key: String): String? {
        return getCoilCacheUrl(context.imageLoader, key)
    }
}

/**
 * 跨组件后台服务启动平台抽象
 * 负责启动 Android 平台特定的 Service，如 Sha1 计算与 Aria2 推送服务
 */
interface ServiceBridge {
    /** 启动 Sha1 提取与推送服务 */
    fun startSha1Service(index: Int)

    /** 启动 Aria2 推送后台 Service */
    fun startSendAria2Service(fileBean: FileBean)
}

class AndroidServiceBridge(private val context: Context) : ServiceBridge {
    override fun startSha1Service(index: Int) {
        // 保留扩展入口
    }

    override fun startSendAria2Service(fileBean: FileBean) {
        val intent = Intent(context, Sha1Service::class.java)
        intent.putExtra(ConfigKeyUtil.COMMAND, ConfigKeyUtil.SENT_TO_ARIA2)
        intent.putExtra("list", Gson().toJson(fileBean))
        context.startService(intent)
    }
}

/**
 * 后台 Worker 及电池优化状态平台抽象
 * 负责 WorkManager 离线任务调度与系统电池优化检测
 */
interface BackgroundScheduler {
    /** 检查或重新调度离线任务扫描 Worker */
    suspend fun handleOfflineTask(currentOfflineTaskRaw: String, forceRecreate: Boolean)

    /** 检查当前应用是否已被加入电池优化白名单 */
    fun isIgnoringBatteryOptimizations(): Boolean
}

class AndroidBackgroundScheduler(
    private val context: Context,
    private val toastProvider: ToastProvider = AndroidToastProvider(context)
) : BackgroundScheduler {
    override suspend fun handleOfflineTask(currentOfflineTaskRaw: String, forceRecreate: Boolean) {
        val workManager = WorkManager.getInstance(context)
        val workQuery = WorkQuery.Builder.fromStates(
            listOf(
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING,
                WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED
            )
        ).build()

        // 统一使用异步挂起，避免阻塞
        val workInfos = workManager.getWorkInfos(workQuery).await()

        if (workInfos.isNotEmpty()) {
            if (forceRecreate) {
                // 如果是强制重新添加，则取消之前的所有任务
                workInfos.forEach { workManager.cancelWorkById(it.id) }
            } else {
                // 如果只是检查，发现已有任务则直接返回
                return
            }
        }

        // 获取并过滤本地缓存任务
        val currentOfflineTask = currentOfflineTaskRaw.split("\n")
            .filter { i -> i.isNotBlank() }
            .toSet().toMutableList()

        if (currentOfflineTask.isEmpty()) {
            // 如果是主动添加模式且列表为空，弹 Toast 提示
            if (forceRecreate) {
                toastProvider.showToast("没有离线任务！")
            }
            return
        }
        toastProvider.showToast("开始下载！")

        // 序列化并提交新任务
        val listType = object : TypeToken<List<String?>?>() {}.type
        val list = Gson().toJson(currentOfflineTask, listType)
        val data = Data.Builder().putString("list", list).build()

        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val request = OneTimeWorkRequest.Builder(OfflineTaskWorker::class.java)
            .addTag(ConfigKeyUtil.OFFLINE_TASK_WORKER).setInputData(data)
            .setConstraints(constraints).build()

        workManager.enqueue(request)
    }

    override fun isIgnoringBatteryOptimizations(): Boolean {
        return context.isIgnoringBatteryOptimizations()
    }
}

/**
 * 跨模块目录导航与浏览动作协同接口
 * 由 FileViewModel 门面实现，向各子领域模块注入，统管 Job 异步生命周期与竞态取消。
 */
interface FileNavigator {
    /** 导航并加载指定 cid 目录（统一受 Facade 的 Job 竞态取消保护，非挂起调用） */
    fun navigateTo(cid: String)

    /** 全量刷新当前目录 */
    fun refresh(forceCache: Boolean = false)

    /** 刷新指定 cid 目录 */
    fun refresh(cid: String, forceCache: Boolean = false)

    /** 更新当前数据源模式的排序规则及混排参数 */
    fun updateOrder(order: OrderBean, fcMix: Int)
}

/**
 * 外部 DeepLink 领域动作抽象（纯 Kotlin 契约）
 */
sealed interface DeepLinkAction {
    data object CheckMagnet : DeepLinkAction
    data class JumpDirectory(val targetCid: String) : DeepLinkAction
    data class CopyText(val content: String, val toastTip: String) : DeepLinkAction
}

/**
 * 解析平台 Intent 为 DeepLinkAction 领域动作
 */
fun parseDeepLink(intent: Intent?): DeepLinkAction? {
    // 解析 Scheme 和 Host: nap511://detail/check?param=3213
    val uri = intent?.data ?: return null
    if (uri.scheme != "nap511" || uri.host != "detail") return null
    val command = uri.lastPathSegment ?: return null
    val param = uri.getQueryParameter("param") ?: ""
    return when (command) {
        "check" -> DeepLinkAction.CheckMagnet
        "jump" -> DeepLinkAction.JumpDirectory(param)
        "copy" -> DeepLinkAction.CopyText(param, "复制磁力链接成功!")
        "unzipError" -> DeepLinkAction.CopyText(param, "解压失败信息已复制到剪切板!")
        else -> null
    }
}
