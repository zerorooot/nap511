package github.zerorooot.nap511.activity

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.concurrent.futures.await
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.R
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.worker.OfflineTaskWorker
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class OfflineTaskActivity : ComponentActivity() {

    companion object {
        const val ACTION_START_IMMEDIATELY =
            "github.zerorooot.nap511.ACTION_START_OFFLINE_IMMEDIATELY"
        const val PENDING_NOTIFICATION_ID = 1000
    }

    // 40位十六进制哈希正则 (BTih v1 标准)
    private val HEX_40_PATTERN = Pattern.compile("^[0-9a-fA-F]{40}$")

    // 32位Base32哈希正则 (早期或简短版磁力链标准)
    private val BASE32_32_PATTERN = Pattern.compile("^[a-zA-Z2-7]{32}$")

    @SuppressLint("EnqueueWork")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == ACTION_START_IMMEDIATELY) {
            startOfflineTaskImmediately()
            return
        }

        val dataUri: Uri? = intent?.data
        if (dataUri == null && intent.action != Intent.ACTION_PROCESS_TEXT) {
            XLog.w("OfflineTaskActivity 未接收到任何链接数据 intent: $intent")
            finishAndRemoveTask()
            return
        }

        var textToHandle: String? = null
        var tag = "未知"

        val scheme = dataUri?.scheme ?: "未知"
        when (scheme) {
            "magnet", "ftp", "ed2k" -> {
                textToHandle = dataUri.toString()
                tag = scheme
            }

            "nap511" -> {
                val encoded = dataUri?.getQueryParameter("param") ?: ""
                textToHandle = Uri.decode(encoded)
                tag = scheme
            }
        }

        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)?.let {
                textToHandle = it
                tag = "EXTRA_PROCESS_TEXT"
            }
        }

        val text = textToHandle
        if (!text.isNullOrEmpty()) {
            handleText(text, tag)
        } else {
            finishAndRemoveTask()
        }
    }

    private fun startOfflineTaskImmediately() {
        lifecycleScope.launch {
            try {
                withContext(NonCancellable) {
                    val workManager = WorkManager.getInstance(this@OfflineTaskActivity)
                    val workQuery = WorkQuery.Builder.fromStates(
                        listOf(
                            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING,
//                    WorkInfo.State.SUCCEEDED,
//                    WorkInfo.State.FAILED,
                            WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED
                        )
                    ).build()

                    // 统一使用异步挂起，避免阻塞
                    val workInfos = workManager.getWorkInfos(workQuery).await()

                    if (workInfos.isNotEmpty()) {
                        workInfos.forEach { workManager.cancelWorkById(it.id) }
                    }

                    // 获取并过滤本地缓存任务
                    val currentOfflineTask =
                        DataStoreUtil.getDataSuspend(ConfigKeyUtil.CURRENT_OFFLINE_TASK, "")
                            .split("\n")
                            .filter { i -> i.isNotBlank() } // 简化过滤逻辑
                            .toSet()
                            .toMutableList()


                    App.instance.toast("开始下载！")
                    XLog.d(
                        "startOfflineTaskImmediately , workInfos size=${workInfos.size}"
                    )

                    // 序列化并提交新任务
                    val listType = object : TypeToken<List<String?>?>() {}.type
                    val list = Gson().toJson(currentOfflineTask, listType)
                    val data = Data.Builder().putString("list", list)
                        .build()

                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    val request = OneTimeWorkRequest.Builder(OfflineTaskWorker::class.java)
                        .addTag(ConfigKeyUtil.OFFLINE_TASK_WORKER)
                        .setConstraints(constraints)
                        .setInputData(data)
                        .build()

                    workManager.enqueue(request)

                    val notificationManager =
                        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(PENDING_NOTIFICATION_ID)
                    App.instance.toast("已开始离线下载")
                }
            } finally {
                finishAndRemoveTask()
            }
        }
    }

    private fun handleText(text: String, tag: String) {
        lifecycleScope.launch {
            try {
                withContext(NonCancellable) {
                    val urlList = text.split("\n").map { i ->
                        //支持复制无头磁力链接
                        val a = i.replace(Regex("&dn=.*"), "").trim()
                        if (HEX_40_PATTERN.matcher(a).matches() || BASE32_32_PATTERN.matcher(a)
                                .matches()
                        ) {
                            "magnet:?xt=urn:btih:$a"
                        } else {
                            a
                        }
                    }.filter { i ->
                        i.startsWith("http", true) || i.startsWith(
                            "ftp", true
                        ) || i.startsWith("magnet", true) || i.startsWith("ed2k", true)
                    }.toSet()

                    XLog.i("OfflineTaskActivity $tag urlList: $urlList")

                    //非空列表
                    if (urlList.isNotEmpty()) {
                        val currentOfflineTaskList =
                            DataStoreUtil.getDataSuspend(ConfigKeyUtil.CURRENT_OFFLINE_TASK, "")
                                .split("\n")
                                .filter { i -> i != "" && i != " " }
                                .toSet()
                                .toMutableSet()
                        //添加所有
                        currentOfflineTaskList.addAll(urlList)
                        //检查离线任务时间
                        val offlineTime = try {
                            DataStoreUtil.getDataSuspend(ConfigKeyUtil.DEFAULT_OFFLINE_TIME, "5")
                                .toLong()
                        } catch (e: Exception) {
                            5L
                        }
                        val stringJoiner = currentOfflineTaskList.toSet().joinToString("\n")
                        //写入缓存
                        DataStoreUtil.putDataSuspend(
                            ConfigKeyUtil.CURRENT_OFFLINE_TASK,
                            stringJoiner
                        )
                        val allPath = DataStoreUtil.getDataSuspend(
                            ConfigKeyUtil.DEFAULT_OFFLINE_PATH,
                            "根目录/云下载"
                        )
                        val path = allPath.substringAfterLast("/")
                        val size = currentOfflineTaskList.size

                        App.instance.toast("已添加到’$path‘，共${size}个链接，${offlineTime}分钟后开始离线下载")
                        showPendingNotification(allPath, path, size)
                        addOfflineTaskByTime(currentOfflineTaskList.toList(), offlineTime)
                    } else {
                        App.instance.toast("仅支持以http、ftp、magnet、ed2k开头的链接")
                    }
                }
            } finally {
                finishAndRemoveTask()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showPendingNotification(allPath: String, path: String, count: Int) {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "offline_pending_channel"
        val channel = NotificationChannel(
            channelId,
            "离线任务待下载通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "离线任务等待下载的通知"
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this, OfflineTaskActivity::class.java).apply {
            action = ACTION_START_IMMEDIATELY
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("点我下载到'$path'")
            .setContentText("将${count}个链接添加到'${allPath}'目录下")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(PENDING_NOTIFICATION_ID, notification)
    }

    @SuppressLint("EnqueueWork")
    private fun addOfflineTaskByTime(
        currentOfflineTaskList: List<String>,
        offlineTime: Long
    ) {
        val listType = object : TypeToken<List<String?>?>() {}.type
        val list = Gson().toJson(currentOfflineTaskList, listType)
        val data: Data = Data.Builder().putString("list", list)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request: OneTimeWorkRequest =
            OneTimeWorkRequest.Builder(OfflineTaskWorker::class.java)
                .setInputData(data)
                .setConstraints(constraints)
                .addTag(ConfigKeyUtil.OFFLINE_TASK_WORKER)
                .setInitialDelay(offlineTime, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                "addOfflineTaskByTime",
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

}
