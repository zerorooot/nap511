package github.zerorooot.nap511.activity

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
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.MainActivity
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.bean.ZipStatus
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.App
import java.io.File
import java.util.StringJoiner
import java.util.stream.Collectors

class UnzipAllFileWorker(
    appContext: Context, workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val NOTIFICATION_ID = 1001

    @Volatile
    private var lastUpdateTime: Long = 0 //用于控制通知更新频率的变量
    private val UPDATE_INTERVAL = 500L // 500毫秒更新一次，避免频繁刷新导致系统丢弃更新
    private val notificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    private val fileRepository: FileRepository by lazy {
        FileRepository.getInstance(App.cookie)
    }
    private val CHANNEL_ID = "unzip_completion_channel"

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "文件解压",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "文件在线解压结果"
            setShowBadge(false) // 进度条不需要应用图标上的小红点
            enableLights(false)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    override suspend fun doWork(): Result {
        createNotificationChannel()
        val sj = StringJoiner("\n")
//        val unzipResultList = arrayListOf<Pair<Boolean, String>>()
        val listType = object : TypeToken<List<Pair<String, String>>?>() {}.type
        //仅传入name、pickCode
        val taskFilePath = inputData.getString("listPath") ?: return Result.failure(
            Data.Builder().putBoolean("state", false).putString("message", "listPath不存在！！")
                .build()
        )
        val file = File(taskFilePath)
        if (!file.exists()) {
            return Result.failure(
                Data.Builder().putBoolean("state", false)
                    .putString("message", "${file.absolutePath}不存在！！")
                    .build()
            )
        }
        val jsonString = file.readText()
        val fileBeanList: List<Pair<String, String>> =
            Gson().fromJson(jsonString, listType)
        file.delete()

        val cid = inputData.getString("cid").toString()
        val password = inputData.getString("pwd")
        XLog.d("UnzipAllFileWorker password:$password  cid: $cid  fileBeanList:$fileBeanList ")

        //设置解压时的通知
        val size = fileBeanList.size
        setForegroundAsync(createForegroundInfo("解压中", "正在解压中", 0, size))

        fileBeanList.forEachIndexed { i, fileBean ->
            val fileName = fileBean.first
            val pickCode = fileBean.second
            var unzipState: Pair<Boolean, String>

            try {
                var zipListFile = getZipListFile(pickCode)

                //加密的文件
                if (zipListFile == null) {
                    //todo 密码错误时的处理
                    if (password != null) {
                        XLog.d("$fileName is encryption zip file password is $password")
                        val decryptZip = fileRepository.decryptZip(pickCode, password)
                        if (decryptZip && fileRepository.tryToExtract(pickCode)) {
                            zipListFile = getZipListFile(pickCode)
                            //查看是否解压成功
                            if (zipListFile != null) {
                                //非加密文件，正常解压
                                XLog.d("UnzipAllFileWorker unzip password word $fileName")
                                unzipState = unzipAllAndDeleteFolderIfUnzipError(
                                    zipListFile, pickCode, cid, fileName
                                )
                            } else {
                                // 修复：解密尝试后依然获取不到列表
                                unzipState = Pair(false, "解压失败，文件列表为空")
                            }
                        } else {
                            // 修复：密码解密请求失败或尝试提取失败
                            unzipState = Pair(false, "密码错误或提取失败")
                        }
                    } else {
                        // 修复原代码逻辑漏洞：加密文件但没有提供密码时，应标记为失败
                        unzipState = Pair(false, "文件已加密，需要提供密码")
                    }
                } else {
                    // 非加密文件，直接解压
                    unzipState =
                        unzipAllAndDeleteFolderIfUnzipError(zipListFile, pickCode, cid, fileName)
                }
            } catch (e: Exception) {
                // 【核心修改点】：在这里捕获 getZipListFile 抛出的异常 (如：暂不支持解压预览20GB以上的压缩包)
                XLog.e("UnzipAllFileWorker 遇到异常: ${e.message}")
                unzipState = Pair(false, e.message ?: "未知解析错误")
            }

            val state = unzipState.first

            // 记录失败日志
            if (!state) {
                sj.add(fileBeanList[i].first + " 解压失败！原因：" + unzipState.second)
            }

            //更新解压进度
            updateNotification(
                "解压中",
                i + 1,
                size,
                (if (state) "$fileName ${i + 1}/$size" else "$fileName 解压失败！${unzipState.second}")
            )
        }


        val result = (sj.toString() == "")

        showCompletionNotification(result, sj.toString(), cid)
        val message = if (result) {
            "${size}个文件解压完成！"
        } else {
            "${size}个文件解压完成！" + sj.toString()
        }

        val addTaskData =
            Data.Builder().putBoolean("state", result).putString("message", message).build()

        App.instance.toast(message)

        return if (result) {
            Result.success(addTaskData)
        } else {
            Result.failure(addTaskData)
        }
    }

    @Synchronized
    private fun updateNotification(
        titleString: String, progress: Int, max: Int, content: String, force: Boolean = false
    ) {
        val currentTime = System.currentTimeMillis()
        // 节流阀逻辑保持不变，这对于性能至关重要
        if (force || currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            lastUpdateTime = currentTime
            XLog.d("updateProgressNotification 已解压: $progress/$max")
            try {
                val build = createNotification(titleString, content, progress, max).build()
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
        zipBeanList: ZipBeanList,
        pickCode: String,
        cid: String,
        fileName: String
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
                // 遇到限制（如>20GB）或接口错误时，抛出异常中断当前文件的操作，并将 message 抛给外层
                throw RuntimeException(status.message)
            }

            is ZipStatus.Encrypted -> {
                if (!fileRepository.tryToExtract(pickCode)) {
                    return null // 保持原有逻辑：返回 null 代表是加密文件且需要密码
                }
            }

            is ZipStatus.Loading -> {
                throw RuntimeException("正在进行云解压，请稍等...(${status.progress}%)")
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
        titleString: String, detailedText: String, progress: Int, max: Int
    ): NotificationCompat.Builder {
        val notificationBuilder =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(titleString)
                .setContentText(detailedText) // 具体内容
                .setAutoCancel(false)
                .setSmallIcon(R.drawable.ic_launcher_foreground).setOnlyAlertOnce(true)
                // [修改] 明确设置为进度类型，这有助于系统正确渲染进度条样式 CATEGORY_SERVICE CATEGORY_PROGRESS
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setStyle(
                    NotificationCompat.ProgressStyle()
                        .setProgress(((progress.toFloat() / max) * 100).toInt())
                        //true=流动条纹, false=具体百分比
                        .setProgressIndeterminate(progress == 0)
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)


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
        val build = createNotification(titleString, detailedText, progress, max).build()
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
    private fun showCompletionNotification(success: Boolean, message: String, cid: String) {
        val channel = NotificationChannel(
            "unzip_completion_channel",
            "文件在线解压结果",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this.applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        if (!success) {
            intent.action = "unzipError"
            intent.putExtra("message", message)
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
                .setContentTitle(if (success) "解压完成" else "部分文件解压失败")
                .setContentText(message)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
        // [新增] 尝试适配 Android 16 (Baklava) 的新特性
        // 注意：目前 SDK 可能还需要预览版支持，这里是一个兼容性写法的示例
        if (Build.VERSION.SDK_INT >= 35) { // 35+ 或 Build.VERSION_CODES.BAKLAVA
            // 这一行让系统知道这是一个高优先级的持续任务（胶囊样式）
            notificationBuilder.setOngoing(true)
            notificationBuilder.setRequestPromotedOngoing(true)
        }

        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notificationBuilder.build()
        )
    }


}