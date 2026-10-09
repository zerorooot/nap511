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
import github.zerorooot.nap511.MainActivity
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.bean.ZipStatus
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.FileCacheManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.StringJoiner
import kotlin.time.Duration.Companion.milliseconds

class UnzipAllFileWorker(
    appContext: Context, workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "unzip_completion_channel"

    @Volatile
    private var lastUpdateTime: Long = 0
    private val UPDATE_INTERVAL = 500L // 500毫秒更新一次，避免频繁刷新导致系统丢弃更新

    private val notificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    private val fileRepository: FileRepository by lazy {
        FileRepository.getInstance()
    }

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

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val (processed, total) = UnzipQueueManager.getProgress()
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
        UnzipQueueManager.markWorkerRunning()

        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            // Android 12+ 在后台无法启动前台服务，忽略异常继续在后台执行短任务
            XLog.w("UnzipAllFileWorker setForeground 失败，将作为普通后台任务继续运行: ${e.message}")
        }

        val resultSummary = StringJoiner("\n")
        val unzipFailList = arrayListOf<FileBean>()
        var lastProcessedItem: UnzipTaskItem? = null

        return@withContext try {
            // === 核心 Drain Loop 动态排空消费循环 ===
            var finalResult: Result? = null
            while (true) {
                while (true) {
                    val taskItem =
                        UnzipQueueManager.pollNext() ?: // 双重检查加锁：若队列真为空则跳出内层循环准备收尾；若此时有新追加，继续消费
                        if (UnzipQueueManager.tryFinishWorker()) {
                            break
                        } else {
                            continue
                        }

                    lastProcessedItem = taskItem
                    val fileBean = taskItem.fileBean
                    val targetCid = taskItem.targetCid
                    affectedCids.add(targetCid)

                    val (currentProcessed, currentTotal) = UnzipQueueManager.getProgress()

                    // 1. 【即时反馈】解压单文件前，立即强制刷新通知栏
                    val startMsg =
                        "正在解压：${fileBean.name} (${currentProcessed + 1}/$currentTotal)"
                    updateNotification(
                        "解压中",
                        currentProcessed,
                        currentTotal,
                        startMsg,
                        targetCid,
                        force = true
                    )

                    // 2. 【细粒度解压】执行解压并支持云端 Loading 状态机轮询
                    val (isSuccess, stateMessage) = processSingleZipFileWithPolling(taskItem) { phaseText ->
                        val (p, t) = UnzipQueueManager.getProgress()
                        updateNotification(
                            "解压中",
                            p,
                            t,
                            "${fileBean.name} $phaseText (${p + 1}/$t)",
                            targetCid
                        )
                    }

                    // 3. 步进完成数并落盘移除该任务
                    UnzipQueueManager.onTaskCompleted(taskItem)
                    val (updatedProcessed, updatedTotal) = UnzipQueueManager.getProgress()

                    // 4. 单任务即时热更新
                    FileCacheManager.notifyRemoteRefresh(targetCid)

                    if (!isSuccess) {
                        resultSummary.add("${fileBean.name} 解压失败！原因：$stateMessage")
                        unzipFailList.add(fileBean)
                    }

                    // 5. 单个文件结束后的进度刷新
                    val progressMsg = if (isSuccess) {
                        "${fileBean.name} 解压完成 (${updatedProcessed}/$updatedTotal)"
                    } else {
                        "${fileBean.name} 解压失败！(${updatedProcessed}/$updatedTotal) $stateMessage"
                    }
                    updateNotification(
                        "解压中",
                        updatedProcessed,
                        updatedTotal,
                        progressMsg,
                        targetCid
                    )
                }

                // 检查在内层跳出后、准备汇总前是否有微秒级新任务追加
                val hasExtraTasks = synchronized(UnzipQueueManager.lock) {
                    if (UnzipQueueManager.hasMore()) {
                        UnzipQueueManager.resumeWorkerRunning()
                        true
                    } else {
                        false
                    }
                }
                if (hasExtraTasks) {
                    continue
                }

                // 确已全部处理完毕，发送统一汇总通知
                val (finalProcessed, _) = UnzipQueueManager.getProgress()
                finalResult = sentMessage(
                    unzipResult = resultSummary.toString(),
                    isCancel = false,
                    name = lastProcessedItem?.fileBean?.name ?: "文件",
                    size = finalProcessed,
                    unzipFailList = unzipFailList,
                    targetCid = lastProcessedItem?.targetCid ?: "0",
                    explicitErrorCid = lastProcessedItem?.errorCid
                )
                break
            }
            finalResult

        } catch (e: CancellationException) {
            XLog.w("UnzipAllFileWorker 任务被取消: ${e.message}")
            UnzipQueueManager.clear()
            val (finalProcessed, _) = UnzipQueueManager.getProgress()
            sentMessage(
                unzipResult = resultSummary.toString(),
                isCancel = true,
                name = lastProcessedItem?.fileBean?.name ?: "文件",
                size = finalProcessed,
                unzipFailList = unzipFailList,
                targetCid = lastProcessedItem?.targetCid ?: "0",
                explicitErrorCid = lastProcessedItem?.errorCid
            )
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
            val maxWaitMs = 5 * 60 * 1000L // 最大轮询等待 5 分钟
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
                fileRepository.unzipFile(pickCode, zipFileCid, files, dirs, fileName, false)
            isExtracted = unzipFile.first
            return unzipFile
        } finally {
            // 若解压未成功（报错或被 CancellationException 取消），立即删除预建的空目录
            if (!isExtracted && zipFileCid.isNotEmpty() && zipFileCid != cid) {
                try {
                    fileRepository.delete(cid, zipFileCid)
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
        App.instance.toast(message)

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
        val data = SettingsRepository.getDataSuspend(ConfigKeyUtil.MOVE_FAIL_FILE, "")
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

    @Synchronized
    private fun updateNotification(
        titleString: String,
        progress: Int,
        max: Int,
        content: String,
        targetCid: String,
        force: Boolean = false
    ) {
        val currentTime = System.currentTimeMillis()
        // 节流阀逻辑保持不变，这对于性能至关重要
        if (force || currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            lastUpdateTime = currentTime
            XLog.v("updateProgressNotification $content")
            try {
                val build =
                    createNotification(titleString, content, "$progress/$max", progress, max)
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
                e.printStackTrace()
            }
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