package github.zerorooot.nap511.activity

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import java.util.StringJoiner
import java.util.stream.Collectors

class UnzipAllFileWorker(
    appContext: Context, workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val notificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    private val fileRepository: FileRepository by lazy {
        FileRepository.getInstance(App.cookie)
    }
    private val notificationId = 1000

    override suspend fun doWork(): Result {
        val sj = StringJoiner("\n")
        val unzipResultList = arrayListOf<Pair<Boolean, String>>()
        val listType = object : TypeToken<List<Pair<String, String>>?>() {}.type
        //todo 为节约资源，仅传入name、pickCode
        val fileBeanList: List<Pair<String, String>> =
            Gson().fromJson(inputData.getString("list").toString(), listType)
        val cid = inputData.getString("cid").toString()
        val password = inputData.getString("pwd")
        XLog.d("UnzipAllFileWorker $password $cid $fileBeanList ")

        //设置解压时的通知
        val size = fileBeanList.size
        setForegroundAsync(createForegroundNotification(size))

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

            // 记录失败日志
            if (!unzipState.first) {
                sj.add(fileBeanList[i].first + " 解压失败！原因：" + unzipState.second)
            }
            unzipResultList.add(unzipState)

            //更新解压进度
            updateProgressNotification(i, size, unzipState.first, unzipState.second)

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
        val unzipFile = fileRepository.unzipFile(pickCode, zipFileCid, files, dirs, false)

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

            is ZipStatus.Normal -> {
                // 正常包，无需拦截，直接去拿文件列表
            }
        }

        return fileRepository.getZipListFile(pickCode)
    }

    /**
     * 创建前台通知
     */
    private fun createForegroundNotification(max: Int): ForegroundInfo {
        val channel = NotificationChannel(
            "unzip_file_channel",
            "文件在线解压进度",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, "unzip_file_channel")
            .setContentTitle("文件解压中...")
            .setContentText("已解压: 1/$max")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(max, 0, false)
            .setOngoing(true)
            .build()

        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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

        val notification =
            NotificationCompat.Builder(applicationContext, "unzip_completion_channel")
                .setContentTitle(if (success) "解压完成" else "部分文件解压失败")
                .setContentText(message)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .build()

        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }

    /**
     * 更新通知的进度条
     */
    private fun updateProgressNotification(
        progress: Int,
        max: Int,
        state: Boolean,
        message: String
    ) {
        val channel = NotificationChannel(
            "unzip_file_channel",
            "文件在线解压进度",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, "unzip_file_channel")
            .setContentTitle("文件解压中...")
            .setContentText(if (state) "已解压: ${progress + 1}/$max" else "解压失败！$message")
            .setProgress(max, progress, false)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true) // 不能滑动取消
            .build()
        XLog.d("updateProgressNotification 已解压: $progress/$max")
        notificationManager.notify(
            notificationId,
            notification
        )
    }
}