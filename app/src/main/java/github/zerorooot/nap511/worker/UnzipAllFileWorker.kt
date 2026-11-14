package github.zerorooot.nap511.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import github.zerorooot.nap511.MainActivity
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.bean.ZipStatus
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.viewmodel.platform.ToastProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.StringJoiner
import kotlin.time.Duration.Companion.milliseconds

@HiltWorker
class UnzipAllFileWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val fileRepository: FileRepository,
    private val unzipQueueManager: UnzipQueueManager,
    private val settingsRepository: SettingsRepository,
    private val toastProvider: ToastProvider
) : CoroutineWorker(appContext, workerParams) {
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "unzip_completion_channel"
    private val maxWaitMs = 20 * 1000L // 最大轮询等待 20 秒

    @Volatile
    private var lastUpdateTime: Long = 0
    private val UPDATE_INTERVAL = 500L // 500毫秒更新一次，避免频繁刷新导致系统丢弃更新

    private val notificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    private val cancelPendingIntent: PendingIntent =
        WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)

    private val affectedCids = mutableSetOf<String>()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "文件解压", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "文件在线解压结果"
            setShowBadge(false) // 进度条不需要应用图标上的小红点
            enableLights(false)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun getJumpPendingIntent(targetCid: String): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = "jump"
            putExtra("cid", targetCid)
        }
        return PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private data class TaskProgressState(
        val item: UnzipTaskItem? = null,
        val status: String = "准备解压任务"
    )

    // 统一维护当前正在处理的任务与状态短语（SST单一数据源，用于多线程通知动态渲染）
    @Volatile
    private var currentTaskState = TaskProgressState()

    /**
     * 线程安全的通知栏进度刷新函数 (State-Driven 状态驱动)
     * 无论何时追加新任务，都会自动结合 currentTaskState 以及最新的 getProgress() 实时重绘通知栏
     * @param item 可选的更新任务项，为 null 时在同步块内复用原有任务，彻底杜绝默认参数锁外求值的竞态
     * @param status 可选的状态文案（如 "正在解压"、"云端解压中 50%"、"解压完成"），为 null 时复用当前最新状态
     * @param force 是否绕过节流限制强制刷新
     */
    @Synchronized
    private fun updateProgress(
        item: UnzipTaskItem? = null,
        status: String? = null,
        force: Boolean = false
    ) {
        val state = synchronized(this) {
            val newItem = item ?: currentTaskState.item
            val newStatus = status ?: currentTaskState.status
            TaskProgressState(newItem, newStatus).also { currentTaskState = it }
        }

        val currentTime = System.currentTimeMillis()
        // 节流阀逻辑保持不变，这对于性能至关重要
        if (!force && currentTime - lastUpdateTime <= UPDATE_INTERVAL) {
            return
        }
        lastUpdateTime = currentTime

        val (p, t) = unzipQueueManager.getProgress()
        val currentItem = state.item
        val currentStatus = state.status

        val (content, shortCritical, targetCid) = if (currentItem == null) {
            // 解决初始化阶段加包：若任务尚未出队，即时刷新“准备解压”的最新队列总数
            XLog.d("UnzipAllFileWorker 刷新初始化通知: $currentStatus ($p/$t)")
            Triple("$currentStatus ($p/$t)...", "$p/$t", "0")
        } else {
            // 解决完成瞬间加包：若当前单文件已完成/失败则当前步数为 p，处理中则为 (p + 1)
            val isDone =
                currentStatus.startsWith("解压完成") || currentStatus.startsWith("解压失败")
            val step = if (isDone) p else (p + 1).coerceAtMost(t)
            val text = "${currentItem.fileBean.name} $currentStatus ($step/$t)"
            XLog.v("UnzipAllFileWorker 刷新通知: $text")
            Triple(text, "$step/$t", currentItem.targetCid)
        }

        try {
            val build = createNotification("解压中", content, shortCritical, p, t)
                .setContentIntent(getJumpPendingIntent(targetCid))
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "取消",
                    cancelPendingIntent
                )
                .build()

            val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(
                    NOTIFICATION_ID, build, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                ForegroundInfo(NOTIFICATION_ID, build)
            }

            setForegroundAsync(foregroundInfo)
            notificationManager.notify(NOTIFICATION_ID, foregroundInfo.notification)
        } catch (e: Exception) {
            XLog.w("UnzipAllFileWorker updateProgress 刷新通知失败: ${e.message}", e)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val (processed, total) = unzipQueueManager.getProgress()
        return createForegroundInfo(
            "解压中",
            "准备解压任务 ($processed/$total)...",
            processed,
            total,
            "0"
        )
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        createNotificationChannel()

        val resultSummary = StringJoiner("\n")
        val unzipFailList = arrayListOf<FileBean>()

        return@withContext try {
            // 挂载动态任务追加监听器：在 markWorkerRunning 之前挂载，杜绝初始化竞态窗口
            unzipQueueManager.onQueueChangeListener = { p, t ->
                XLog.i("UnzipAllFileWorker 监听到队列追加新任务: 已处理 $p, 当前总数 $t，即时刷新通知栏")
                updateProgress(force = true)
            }
            unzipQueueManager.markWorkerRunning()

            try {
                setForeground(getForegroundInfo())
            } catch (e: Exception) {
                // Android 12+ 在后台无法启动前台服务，忽略异常继续在后台执行短任务
                XLog.w("UnzipAllFileWorker setForeground 失败，将作为普通后台任务继续运行: ${e.message}")
            }

            // === 核心 Drain Loop 动态排空消费循环 ===
            var finalResult: Result
            while (true) {
                while (true) {
                    val taskItem =
                        unzipQueueManager.pollNext() ?: // 双重检查加锁：若队列真为空则跳出内层循环准备收尾；若此时有新追加，继续消费
                        if (unzipQueueManager.tryFinishWorker()) {
                            break
                        } else {
                            continue
                        }

                    val fileBean = taskItem.fileBean
                    val targetCid = taskItem.targetCid
                    affectedCids.add(targetCid)

                    // 1. 【即时反馈】解压单文件前，原子绑定新任务并立即强制刷新通知栏
                    updateProgress(item = taskItem, status = "正在解压", force = true)

                    // 2. 【细粒度解压】执行解压并支持云端 Loading 状态机轮询
                    val (isSuccess, stateMessage) = processSingleZipFileWithPolling(taskItem) { phaseText ->
                        updateProgress(status = phaseText)
                    }

                    // 3. 步进完成数并落盘移除该任务
                    unzipQueueManager.onTaskCompleted(taskItem)

                    if (!isSuccess) {
                        resultSummary.add("${fileBean.name} 解压失败！原因：$stateMessage")
                        unzipFailList.add(fileBean)
                    }

                    // 4. 单个文件结束后的进度刷新
                    val finishPhase = if (isSuccess) "解压完成" else "解压失败！$stateMessage"
                    updateProgress(status = finishPhase)
                }

                // 检查在内层跳出后、准备汇总前是否有微秒级新任务追加
                val hasExtraTasks = synchronized(unzipQueueManager.lock) {
                    if (unzipQueueManager.hasMore()) {
                        unzipQueueManager.resumeWorkerRunning()
                        true
                    } else {
                        false
                    }
                }
                if (hasExtraTasks) {
                    continue
                }

                // 确已全部处理完毕，卸载监听器后发送统一汇总通知
                unzipQueueManager.onQueueChangeListener = null
                val lastItem = currentTaskState.item
                currentTaskState = TaskProgressState()

                val (finalProcessed, _) = unzipQueueManager.getProgress()
                finalResult = sentMessage(
                    unzipResult = resultSummary.toString(),
                    isCancel = false,
                    name = lastItem?.fileBean?.name ?: "文件",
                    size = finalProcessed,
                    unzipFailList = unzipFailList,
                    targetCid = lastItem?.targetCid ?: "0",
                    explicitErrorCid = lastItem?.errorCid
                )
                break
            }
            finalResult

        } catch (e: CancellationException) {
            XLog.w("UnzipAllFileWorker 任务被取消: ${e.message}")
            withContext(NonCancellable) {
                unzipQueueManager.onQueueChangeListener = null
                val lastItem = currentTaskState.item
                currentTaskState = TaskProgressState()
                val (finalProcessed, _) = unzipQueueManager.getProgress()
                unzipQueueManager.clear()
                sentMessage(
                    unzipResult = resultSummary.toString(),
                    isCancel = true,
                    name = lastItem?.fileBean?.name ?: "文件",
                    size = finalProcessed,
                    unzipFailList = unzipFailList,
                    targetCid = lastItem?.targetCid ?: "0",
                    explicitErrorCid = lastItem?.errorCid
                )
            }
        } finally {
            // 全局生命周期兜底清理
            unzipQueueManager.onQueueChangeListener = null
            currentTaskState = TaskProgressState()
        }
    }

    /**
     * 单文件解压处理：内嵌云解压轮询状态机 (Polling Loop)
     */
    private suspend fun processSingleZipFileWithPolling(
        item: UnzipTaskItem,
        onProgressUpdate: (String) -> Unit
    ): Pair<Boolean, String> {
        val fileBean = item.fileBean
        val pickCode = fileBean.pickCode
        val targetCid = item.targetCid
        val password = item.password
        val fileName = fileBean.name

        try {
            var zipListFile: ZipBeanList?
            val startTime = System.currentTimeMillis()

            // 轮询检查解压状态 (支持云端异步 Loading 状态轮询)
            while (true) {
                when (val status = fileRepository.checkZipStatus(pickCode)) {
                    is ZipStatus.Normal -> {
                        zipListFile = fileRepository.getZipListFile(pickCode)
                        break
                    }

                    is ZipStatus.Encrypted -> {
                        if (password.isNullOrEmpty()) {
                            return Pair(false, "文件已加密，需要提供密码")
                        }
                        val decrypted = fileRepository.decryptZip(pickCode, password)
                        if (!decrypted || !fileRepository.tryToExtract(pickCode)) {
                            return Pair(false, "密码错误或提取失败")
                        }
                        // 解密/提取触发成功后，继续下一轮循环检查状态
                    }

                    is ZipStatus.Loading -> {
                        val progress = status.progress
                        onProgressUpdate("云端解压中 ${progress}%")
                        if (System.currentTimeMillis() - startTime > maxWaitMs) {
                            return Pair(false, "云端解压超时(已等待5分钟)")
                        }
                        delay(2000L.milliseconds) // 间隔 2 秒轮询
                    }

                    is ZipStatus.UnsupportedOrError -> {
                        val message = status.message
                        if (message == "任务被取消") {
                            throw CancellationException(message)
                        }
                        return Pair(false, message)
                    }
                }
            }

            return unzipAllAndDeleteFolderIfUnzipError(zipListFile, pickCode, targetCid, fileName)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            XLog.e("UnzipAllFileWorker processSingleZipFileWithPolling 异常: ${e.message}", e)
            return Pair(false, e.message ?: "未知解析错误")
        }
    }

    /**
     * 新建文件夹，解压；若解压未成功（报错或中途被取消），清理创建的空文件夹
     */
    private suspend fun unzipAllAndDeleteFolderIfUnzipError(
        zipBeanList: ZipBeanList, pickCode: String, cid: String, fileName: String
    ): Pair<Boolean, String> {
        val zipFileCid = fileRepository.createFolderAndReturnCid(cid, fileName)
        var isExtracted = false
        try {
            val dirs = zipBeanList.list.filter { it.fileIco == R.drawable.folder }
                .map { it.fileName }.takeIf { it.isNotEmpty() }
            val files = zipBeanList.list.filter { it.fileIco != R.drawable.folder }
                .map { it.fileName }.takeIf { it.isNotEmpty() }
            val unzipFile =
                fileRepository.unzipFile(pickCode, zipFileCid, files, dirs, fileName)
            isExtracted = unzipFile.first
            return unzipFile
        } finally {
            // 若解压未成功（报错或被 CancellationException 取消），立即删除预建的空目录
            if (!isExtracted && zipFileCid.isNotEmpty() && zipFileCid != cid) {
                try {
                    withContext(NonCancellable) {
                        fileRepository.delete(cid, zipFileCid)
                    }
                    XLog.d("成功清理未完成或解压失败产生的空目录: $zipFileCid")
                } catch (cleanupEx: Exception) {
                    XLog.w("清理空目录异常: ${cleanupEx.message}")
                }
            }
        }
    }

    private suspend fun sentMessage(
        unzipResult: String,
        isCancel: Boolean,
        name: String,
        size: Int,
        unzipFailList: List<FileBean>,
        targetCid: String,
        explicitErrorCid: String?
    ): Result {
        // 1. 确定状态和提示信息
        val isAllSuccess = !isCancel && unzipResult.isEmpty()
        val message = when {
            isCancel -> "🔙任务被取消"
            (size == 1) -> if (isAllSuccess) "$name 解压完成" else "$name 解压失败"
            isAllSuccess -> "${size}个文件解压完成！"
            else -> "❎ ${unzipFailList.size}个文件解压失败！"
        }

        // 2. 统一处理通知、日志和 Toast
        showCompletionNotification(isAllSuccess, message, unzipResult, targetCid)
        XLog.i("UnzipAllFileWorker 完成提示: $message $unzipResult")
        toastProvider.showToast(message)

        // 3. 统一处理失败文件的移动（仅对明确判负的文件归类）
        val moveFailResult = handleFailedFiles(unzipFailList, targetCid, explicitErrorCid)

        // 4.  刷新所有受影响目录的缓存
        affectedCids.forEach { cid ->
            FileCacheManager.notifyRemoteRefresh(cid)
        }
        moveFailResult?.targetErrorCid?.let { errorCid ->
            if (errorCid.isNotEmpty()) {
                FileCacheManager.notifyRemoteRefresh(errorCid)
            }
        }

        // 5. 统一构建返回的 Data
        val finalData = Data.Builder()
            .putBoolean("state", isAllSuccess)
            .putString("message", message)
            .apply { moveFailResult?.message?.let { putString("moveResult", it) } }
            .build()

        return if (isAllSuccess) Result.success(finalData) else Result.failure(finalData)
    }

    private data class MoveFailResult(
        val message: String,
        val targetErrorCid: String?
    )

    private suspend fun handleFailedFiles(
        unzipFailList: List<FileBean>,
        defaultCid: String,
        explicitErrorCid: String?
    ): MoveFailResult? {
        val data = settingsRepository
            .getDataSuspend(ConfigKeyUtil.MOVE_FAIL_FILE, "")
        if (data.isEmpty()) {
            XLog.d("handleFailedFiles 不移动解压失败的文件")
            return null
        }
        if (unzipFailList.isNotEmpty()) {
            return moveFailFile(defaultCid, data, unzipFailList, explicitErrorCid)
        }
        return null
    }

    /**
     * 解压失败的压缩包移动到错误文件夹，并返回目标错误文件夹的 cid
     */
    private suspend fun moveFailFile(
        cid: String,
        folderName: String,
        unzipFailList: List<FileBean>,
        explicitErrorCid: String?
    ): MoveFailResult {
        XLog.d("UnzipAllFileWorker moveFailFile unzipFailList $unzipFailList")
        try {
            var createFolderCid = explicitErrorCid
            if (createFolderCid.isNullOrEmpty()) {
                val createFolderMsg = fileRepository.createFolder(cid, folderName)
                XLog.d("UnzipAllFileWorker create error folder $createFolderMsg")
                createFolderCid = createFolderMsg.cid
            }

            if (createFolderCid.isEmpty()) {
                return MoveFailResult("创建或获取错误文件夹失败！", null)
            }

            val removeFile = fileRepository.removeAllFile(createFolderCid, unzipFailList)
                .also { XLog.d("UnzipAllFileWorker moveFailFile $it") }

            if (!removeFile.state) {
                return MoveFailResult("移动文件失败！ $removeFile", null)
            }

            return MoveFailResult("移动成功！", createFolderCid)
        } catch (e: Exception) {
            return MoveFailResult("移动失败！${e.message}", null)
        }
    }

    private fun createNotification(
        titleString: String, detailedText: String, shortCritical: String, progress: Int, max: Int
    ): NotificationCompat.Builder {
        val notificationBuilder =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(titleString)
                .setContentText(detailedText)
                .setAutoCancel(false)
                .setSmallIcon(R.drawable.ic_splash_cloud_logo)
                .setOnlyAlertOnce(true)
                .setShortCriticalText(shortCritical)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setStyle(
                    NotificationCompat.ProgressStyle()
                        .setProgress(((progress.toFloat() / max) * 100).toInt())
                        .setProgressIndeterminate(progress == 0)
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)

        if (Build.VERSION.SDK_INT >= 35) {
            notificationBuilder.setOngoing(true)
            notificationBuilder.setRequestPromotedOngoing(true)
        }

        notificationBuilder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        return notificationBuilder
    }

    private fun createForegroundInfo(
        titleString: String, detailedText: String, progress: Int, max: Int, targetCid: String
    ): ForegroundInfo {
        val build =
            createNotification(titleString, detailedText, "初始化", progress, max)
                .setContentIntent(getJumpPendingIntent(targetCid))
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ForegroundInfo(
                NOTIFICATION_ID, build, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }
        return ForegroundInfo(NOTIFICATION_ID, build)
    }

    private fun showCompletionNotification(
        success: Boolean, message: String, info: String, cid: String
    ) {
        val channel = NotificationChannel(
            "unzip_completion_channel", "文件在线解压结果", NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Intent.ACTION_VIEW
        }

        if (!success) {
            intent.data = "nap511://detail/unzipError?param=$info".toUri()
            intent.putExtra("message", info)
        } else {
            intent.data = "nap511://detail/jump?param=$cid".toUri()
            intent.putExtra("cid", cid)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder =
            NotificationCompat.Builder(applicationContext, "unzip_completion_channel")
                .setContentTitle(if (success) "解压完成" else "解压失败")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_splash_cloud_logo)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)

        if (Build.VERSION.SDK_INT >= 35) {
            notificationBuilder.setOngoing(true)
            notificationBuilder.setRequestPromotedOngoing(true)
        }

        notificationManager.notify(
            System.currentTimeMillis().toInt(), notificationBuilder.build()
        )
    }
}