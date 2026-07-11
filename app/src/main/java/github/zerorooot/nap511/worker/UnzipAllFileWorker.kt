package github.zerorooot.nap511.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.MainActivity
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.DecompressionLoadingException
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.bean.ZipStatus
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.StringJoiner
import java.util.stream.Collectors

class UnzipAllFileWorker(
    appContext: Context, workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val NOTIFICATION_ID = 1001

    private val CHANNEL_ID = "unzip_completion_channel"

    @Volatile
    private var lastUpdateTime: Long = 0 //用于控制通知更新频率的变量
    private val UPDATE_INTERVAL = 500L // 500毫秒更新一次，避免频繁刷新导致系统丢弃更新
    private val notificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    private val fileRepository: FileRepository by lazy {
        FileRepository.getInstance(App.cookie)
    }
    private val cid: String by lazy {
        inputData.getString("cid").toString()
    }
    private val jumpPendingIntent: PendingIntent by lazy {
        val intent = Intent(this.applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = "jump"
            putExtra("cid", cid)
        }
        PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // 1. 创建取消任务的 PendingIntent (这里的 getId() 是 Worker 自带的方法，获取当前任务ID)
    val cancelPendingIntent = WorkManager.getInstance(applicationContext)
        .createCancelPendingIntent(id)

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

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        createNotificationChannel()
        // 1. 获取并校验文件列表
        val taskFilePath = inputData.getString("listPath")
            ?: return@withContext createFailureResult("listPath不存在！！")

        val fileBeanList = parseFileList(taskFilePath)
            ?: return@withContext createFailureResult("$taskFilePath 不存在！！")

        val password = inputData.getString("pwd")
        XLog.d("UnzipAllFileWorker password:$password  cid: $cid  fileBeanList:$fileBeanList")

        // 2. 初始化进度和通知
        val size = fileBeanList.size
        setForegroundAsync(createForegroundInfo("解压中", "正在解压中", 0, size))

        val sj = StringJoiner("\n")
        val unzipFailList = arrayListOf<FileBean>()

        // 3. 遍历处理文件
        try {
            fileBeanList.forEachIndexed { i, fileBean ->
                // 核心解压逻辑抽离到了 processSingleZipFile
                val (isSuccess, stateMessage) = processSingleZipFile(fileBean, password)

                if (!isSuccess) {
                    sj.add("${fileBean.name} 解压失败！原因：$stateMessage")
                    unzipFailList.add(fileBean)
                }

                // 更新解压进度
                val progressMsg = if (isSuccess) {
                    "${fileBean.name} ${i + 1}/$size"
                } else {
                    "${fileBean.name} 解压失败！${i + 1}/$size $stateMessage "
                }
                updateNotification("解压中", i + 1, size, progressMsg)
            }
            return@withContext sentMessage(sj.toString(), false, size, unzipFailList)
        } catch (e: CancellationException) {
            XLog.e("UnzipAllFileWorker CancellationException 任务被取消: ${e.message}")
        }

        return@withContext sentMessage(sj.toString(), true, size, unzipFailList)

    }

    private suspend fun sentMessage(
        unzipResult: String,
        isCancel: Boolean,
        size: Int,
        unzipFailList: List<FileBean>
    ): Result {
        // 1. 确定状态和提示信息
        val isAllSuccess = !isCancel && unzipResult.isEmpty()
        val message = when {
            isCancel -> "🔙任务被取消"
            isAllSuccess -> "${size}个文件解压完成！"
            else -> "❎ ${unzipFailList.size}个文件解压失败！"
        }

        // 2. 统一处理通知、日志和 Toast
        showCompletionNotification(isAllSuccess, message, unzipResult, cid)
        XLog.d("$message\n$unzipResult")
        App.instance.toast(message)

        // 3. 统一构建返回的 Data
        val moveResultMsg = handleFailedFiles(unzipFailList)
        val finalData = Data.Builder()
            .putBoolean("state", isAllSuccess)
            .putString("message", message)
            .apply { moveResultMsg?.let { putString("moveResult", it) } }
            .build()

        // 4. 根据状态返回成功或失败
        return if (isAllSuccess) Result.success(finalData) else Result.failure(finalData)
    }

    private suspend fun processSingleZipFile(
        fileBean: FileBean,
        password: String?
    ): Pair<Boolean, String> {
        val fileName = fileBean.name
        val pickCode = fileBean.pickCode
        try {
            var zipListFile = getZipListFile(pickCode)

            // 1. 如果是非加密文件，直接解压
            if (zipListFile != null) {
                return unzipAllAndDeleteFolderIfUnzipError(zipListFile, pickCode, cid, fileName)
            }

            // 2. 加密文件的处理逻辑
            if (password == null) {
                return Pair(false, "文件已加密，需要提供密码")
            }

            XLog.d("$fileName is encryption zip file password is $password")
            val decryptZip = fileRepository.decryptZip(pickCode, password)

            if (decryptZip && fileRepository.tryToExtract(pickCode)) {
                zipListFile = getZipListFile(pickCode)
                if (zipListFile != null) {
                    XLog.d("UnzipAllFileWorker unzip password word $fileName")
                    return unzipAllAndDeleteFolderIfUnzipError(zipListFile, pickCode, cid, fileName)
                } else {
                    return Pair(false, "解压失败，文件列表为空")
                }
            } else {
                return Pair(false, "密码错误或提取失败")
            }

        } catch (e: DecompressionLoadingException) {
            val message = e.message ?: run { "正在进行云解压，请稍等..." }
            XLog.d("UnzipAllFileWorker DecompressionLoadingException ${fileBean.name} : ${e.message}")
            return Pair(false, message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            XLog.e("UnzipAllFileWorker Exception 遇到异常: ${e.message}")
            e.printStackTrace()
            return Pair(false, e.message ?: "未知解析错误")
        }
    }

    private fun createFailureResult(message: String): Result {
        val failureData = Data.Builder()
            .putBoolean("state", false)
            .putString("message", message)
            .build()
        return Result.failure(failureData)
    }

    private suspend fun handleFailedFiles(unzipFailList: List<FileBean>): String? {
        val data = DataStoreUtil.getData(ConfigKeyUtil.MOVE_FAIL_FILE, "")
        if (unzipFailList.isNotEmpty() && data.isNotEmpty()) {
            return moveFailFile(cid, data, unzipFailList)
        }
        return null
    }

    /**
     * 解压失败的压缩包移动到某文件
     */
    private suspend fun moveFailFile(
        cid: String, folderName: String, unzipFailList: List<FileBean>
    ): String {
        XLog.d("UnzipAllFileWorker moveFailFile unzipFailList $unzipFailList")
        try {
            val createFolderCid = inputData.getString("errorCid")
                ?: fileRepository.createFolder(cid, folderName)
                    .run { XLog.d("UnzipAllFileWorker createFolder $this"); this.cid }

            val removeFile = fileRepository.removeFile(createFolderCid, unzipFailList)
                .also { XLog.d("UnzipAllFileWorker moveFailFile $it") }

            if (!removeFile.state) {
                return "移动文件失败！ $removeFile"
            }
        } catch (e: Exception) {
            return "移动失败！${e.message}"
        }

        return "移动成功！"
    }

    private fun parseFileList(taskFilePath: String): List<FileBean>? {
        val file = File(taskFilePath)
        if (!file.exists()) return null

        val jsonString = file.readText()
        val listType = object : TypeToken<List<FileBean>>() {}.type
        val fileBeanList: List<FileBean> = Gson().fromJson(jsonString, listType)

        // 读取完成后删除临时文件
        file.delete()
        return fileBeanList
    }

    @Synchronized
    private fun updateNotification(
        titleString: String, progress: Int, max: Int, content: String, force: Boolean = false
    ) {
        val currentTime = System.currentTimeMillis()
        // 节流阀逻辑保持不变，这对于性能至关重要
        if (force || currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            lastUpdateTime = currentTime
            XLog.d("updateProgressNotification $content")
            try {
                val build =
                    createNotification(titleString, content, "$progress/$max", progress, max)
                        .setContentIntent(jumpPendingIntent)
                        .addAction(
                            android.R.drawable.ic_menu_close_clear_cancel,
                            "取消",
                            cancelPendingIntent
                        )
                        .build()
                notificationManager.notify(NOTIFICATION_ID, build)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 新建文件夹，解压，如果解压失败，则删除创建的空文件夹
     */
    private suspend fun unzipAllAndDeleteFolderIfUnzipError(
        zipBeanList: ZipBeanList, pickCode: String, cid: String, fileName: String
    ): Pair<Boolean, String> {
        val zipFileCid = fileRepository.createFolderAndReturnCid(cid, fileName)
        val dirs = zipBeanList.list.stream().filter { i -> i.fileIco == R.drawable.folder }
            .map { a -> a.fileName }.collect(Collectors.toList()).takeIf {
                it.isNotEmpty()
            }
        val files = zipBeanList.list.stream().filter { i -> i.fileIco != R.drawable.folder }
            .map { a -> a.fileName }.collect(Collectors.toList()).takeIf {
                it.isNotEmpty()
            }
        val unzipFile = fileRepository.unzipFile(pickCode, zipFileCid, files, dirs, fileName, false)

        if (!unzipFile.first && zipFileCid != cid) {
            fileRepository.delete(cid, zipFileCid)
        }
        return unzipFile

    }

    private suspend fun getZipListFile(
        pickCode: String
    ): ZipBeanList? {
        // 调用重构后的方法
        when (val status = fileRepository.checkZipStatus(pickCode)) {
            is ZipStatus.UnsupportedOrError -> {
                val message = status.message
                if (message == "任务被取消") {
                    throw CancellationException(message)
                }
                // 遇到限制（如>20GB）或接口错误时，抛出异常中断当前文件的操作，并将 message 抛给外层
                throw RuntimeException(message)
            }

            is ZipStatus.Encrypted -> {
                if (!fileRepository.tryToExtract(pickCode)) {
                    return null // 保持原有逻辑：返回 null 代表是加密文件且需要密码
                }
            }

            is ZipStatus.Loading -> {
                throw DecompressionLoadingException("正在进行云解压，请稍等...(${status.progress}%)")
            }

            is ZipStatus.Normal -> {
                // 正常包，无需拦截，直接去拿文件列表
            }
        }

        return fileRepository.getZipListFile(pickCode)
    }

    /**
     * 创建前台通知
     */
    private fun createNotification(
        titleString: String, detailedText: String, shortCritical: String, progress: Int, max: Int
    ): NotificationCompat.Builder {
        val notificationBuilder =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(titleString)
                .setContentText(detailedText) // 具体内容
                .setAutoCancel(false)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOnlyAlertOnce(true)
                .setShortCriticalText(shortCritical)
                // [修改] 明确设置为进度类型，这有助于系统正确渲染进度条样式 CATEGORY_SERVICE CATEGORY_PROGRESS
                .setCategory(NotificationCompat.CATEGORY_PROGRESS).setStyle(
                    NotificationCompat.ProgressStyle()
                        .setProgress(((progress.toFloat() / max) * 100).toInt())
                        //true=流动条纹, false=具体百分比
                        .setProgressIndeterminate(progress == 0)
                ).setPriority(NotificationCompat.PRIORITY_MAX)


        // [新增] 尝试适配 Android 16 (Baklava) 的新特性
        // 注意：目前 SDK 可能还需要预览版支持，这里是一个兼容性写法的示例
        if (Build.VERSION.SDK_INT >= 35) { // 35+ 或 Build.VERSION_CODES.BAKLAVA
            // 这一行让系统知道这是一个高优先级的持续任务（胶囊样式）
            notificationBuilder.setOngoing(true)
            notificationBuilder.setRequestPromotedOngoing(true)
        }

        // 适配 Android 12+ 立即显示
        notificationBuilder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        return notificationBuilder
    }

    private fun createForegroundInfo(
        titleString: String, detailedText: String, progress: Int, max: Int
    ): ForegroundInfo {
        val build =
            createNotification(titleString, detailedText, "⚙\uFE0F初始化", progress, max).build()
        // Android 14 前台服务类型适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ForegroundInfo(
                NOTIFICATION_ID, build, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }

        return ForegroundInfo(NOTIFICATION_ID, build)
    }

    /**
     * 显示下载完成/失败通知
     */
    private fun showCompletionNotification(
        success: Boolean, message: String, info: String, cid: String
    ) {
        val channel = NotificationChannel(
            "unzip_completion_channel", "文件在线解压结果", NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this.applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        if (!success) {
            intent.action = "unzipError"
            intent.putExtra("message", info)
        } else {
            intent.action = "jump"
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
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
        if (Build.VERSION.SDK_INT >= 35) { // 35+ 或 Build.VERSION_CODES.BAKLAVA
            // 这一行让系统知道这是一个高优先级的持续任务（胶囊样式）
            notificationBuilder.setOngoing(true)
            notificationBuilder.setRequestPromotedOngoing(true)
        }

        notificationManager.notify(
            System.currentTimeMillis().toInt(), notificationBuilder.build()
        )
    }


}