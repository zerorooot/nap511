package github.zerorooot.nap511.activity

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.MainActivity
import github.zerorooot.nap511.R
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import java.util.StringJoiner
import java.util.concurrent.TimeUnit


class OfflineTaskActivity : ComponentActivity() {
    @SuppressLint("EnqueueWork")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_PROCESS_TEXT) {
            val urlList = (intent.dataString ?: run {
                intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            } ?: run { "" }).split("\n").map { i ->
                //支持复制无头磁力链接
                val a = i.replace(Regex("&dn=.*"), "")
                if (a.length == 40 && Regex("^[a-z0-9A-Z]+\$").matches(a)) {
                    "magnet:?xt=urn:btih:$i"
                } else {
                    i
                }
            }.filter { i ->
                i.startsWith("http", true) || i.startsWith(
                    "ftp", true
                ) || i.startsWith("magnet", true) || i.startsWith("ed2k", true)
            }.toSet()
//            App.instance.toast(urlList.toString())
            println(urlList.toString())
            //非空列表
            if (urlList.isNotEmpty()) {
                val currentOfflineTaskList =
                    DataStoreUtil.getData(ConfigKeyUtil.CURRENT_OFFLINE_TASK, "")
                        .split("\n")
                        .filter { i -> i != "" && i != " " }
                        .toSet()
                        .toMutableSet()
                //添加所有
                currentOfflineTaskList.addAll(urlList)
                //离线任务缓存方式,true为x分钟后统一下载，false为集满后统一下载
                if (DataStoreUtil.getData(ConfigKeyUtil.OFFLINE_METHOD, true)) {
                    addOfflineTaskByTime(currentOfflineTaskList.toList())
                } else {
                    addOfflineTaskByCount(currentOfflineTaskList.toList())
                }
            } else {
                App.instance.toast("仅支持以http、ftp、magnet、ed2k开头的链接")
            }
        }
        //通过ACTION_PROCESS_TEXT添加磁力链接时，如果moveTaskToBack(true)，当前应用会回到桌面
        if (intent.action != Intent.ACTION_PROCESS_TEXT) {
            moveTaskToBack(true);
        }

        finishAndRemoveTask()
    }

    @SuppressLint("EnqueueWork")
    private fun addOfflineTaskByTime(currentOfflineTaskList: List<String>) {
        //检查离线任务时间
        val offlineTime = try {
            DataStoreUtil.getData(ConfigKeyUtil.DEFAULT_OFFLINE_TIME, "5").toLong()
        } catch (e: Exception) {
            5L
        }
        val stringJoiner = StringJoiner("\n")
        currentOfflineTaskList.toSet().forEach { stringJoiner.add(it) }
        //写入缓存
        DataStoreUtil.putData(
            ConfigKeyUtil.CURRENT_OFFLINE_TASK,
            stringJoiner.toString()
        )

        App.instance.toast("已添加 ${currentOfflineTaskList.size} 个链接，${offlineTime}分钟后开始离线下载")
        val listType = object : TypeToken<List<String?>?>() {}.type
        val list = Gson().toJson(currentOfflineTaskList, listType)
        val data: Data =
            Data.Builder().putString("cookie", App.cookie).putString("list", list)
                .build()
        val request: OneTimeWorkRequest =
            OneTimeWorkRequest.Builder(OfflineTaskWorker::class.java).setInputData(data)
                .setInitialDelay(offlineTime, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                "addOfflineTaskByTime",
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

    private fun addOfflineTaskByCount(currentOfflineTaskList: List<String>) {
        //检查离线任务缓存数
        val offlineCount = try {
            DataStoreUtil.getData(ConfigKeyUtil.DEFAULT_OFFLINE_COUNT, "5").toInt()
        } catch (e: Exception) {
            5
        }
        if (currentOfflineTaskList.size >= offlineCount) {
            App.instance.toast("${currentOfflineTaskList.size} 个链接添加中......")
            val listType = object : TypeToken<List<String?>?>() {}.type
            val list = Gson().toJson(currentOfflineTaskList, listType)
            val data: Data =
                Data.Builder().putString("cookie", App.cookie).putString("list", list)
                    .build()
            val request: OneTimeWorkRequest =
                OneTimeWorkRequest.Builder(OfflineTaskWorker::class.java).setInputData(data)
                    .build()
//            WorkManager.getInstance(applicationContext).enqueue(request)
            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(
                    "addOfflineTaskByCount",
                    ExistingWorkPolicy.APPEND,
                    request
                )
        } else {
            val stringJoiner = StringJoiner("\n")
            currentOfflineTaskList.forEach { stringJoiner.add(it) }
            //写入缓存
            DataStoreUtil.putData(
                ConfigKeyUtil.CURRENT_OFFLINE_TASK,
                stringJoiner.toString()
            )
            App.instance.toast("已添加${currentOfflineTaskList.size}个链接到缓存中，剩余${offlineCount - currentOfflineTaskList.size}个")
        }
    }
}


class OfflineTaskWorker(
    appContext: Context, workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val listType = object : TypeToken<List<String?>?>() {}.type
        val a: List<String> = Gson().fromJson(inputData.getString("list").toString(), listType)
        val cid = DataStoreUtil.getData(ConfigKeyUtil.DEFAULT_OFFLINE_CID, "")

        XLog.d("OfflineTaskWorker cid $cid")
        App.offlineFileViewModel.addTask(a, cid)
        Thread.sleep(5000)
        val state = App.offlineFileViewModel.addTaskReturn.first
        val message = App.offlineFileViewModel.addTaskReturn.second
        if (state) {
            //清空缓存
            DataStoreUtil.putData(
                ConfigKeyUtil.CURRENT_OFFLINE_TASK,
                ""
            )
        }
        println("checkOfflineTask $message")
        toast(message, a)
        val addTaskData = Data.Builder()
            .putBoolean("state", state)
            .putString("return", message)
            .build()
        return if (state) {
            Result.success(addTaskData);
        } else {
            Result.failure(addTaskData)
        }
    }


    @SuppressLint("WrongConstant")
    private fun toast(message: String, urlList: List<String>) {
        //渠道Id
        val channelId = "toast"
        //渠道名
        val channelName = "离线下载结果"
        //渠道重要级
        val importance = NotificationManagerCompat.IMPORTANCE_MAX
        //通知Id
        val notificationId = System.currentTimeMillis().toInt()

        val notificationManager =
            applicationContext.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
        //创建通知渠道
        notificationManager.createNotificationChannel(
            NotificationChannel(
                channelId, channelName, importance
            )
        )
        val notification = NotificationCompat.Builder(applicationContext, channelId).apply {
            setSmallIcon(R.mipmap.ic_launcher)
            setContentTitle("离线下载结果")//标题
            setAutoCancel(true)
//                setContentText(message)
            setDefaults(Notification.DEFAULT_VIBRATE);
            setStyle(NotificationCompat.BigTextStyle().bigText(message))
        }

        val intent = Intent(this.applicationContext, MainActivity::class.java)
 //       intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        val pendingIntent = if (message.contains("任务添加失败")) {
            if (message.contains("请验证账号")) {
                intent.action = "check"
                notification.setContentText("$message。点我跳转验证账号页面")
            } else {
                intent.action = "copy"
                val stringJoiner = StringJoiner("\n")
                urlList.forEach { stringJoiner.add(it) }
                intent.putExtra("link", stringJoiner.toString())
                notification.setContentText("$message。点我复制链接")
            }
            PendingIntent.getActivity(
                this.applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            intent.action = "jump"
            notification.setContentText(message)
            PendingIntent.getActivity(
                this.applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
        }

        notification.setContentIntent(pendingIntent)
        notificationManager.notify(notificationId, notification.build())
    }
}