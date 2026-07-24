package github.zerorooot.nap511.worker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.MainActivity
import github.zerorooot.nap511.R
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import java.util.StringJoiner

class OfflineTaskWorker(
    appContext: Context, workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val fileRepository: FileRepository by lazy {
        FileRepository.getInstance(App.cookie)
    }

    override suspend fun doWork(): Result {
        val listType = object : TypeToken<List<String?>?>() {}.type
        val a: List<String> = Gson().fromJson(inputData.getString("list").toString(), listType)
        val cid = inputData.getString("defaultOfflineCid").toString()
        val addTaskReturn = fileRepository.addOfflineTask(a, cid) {}

        XLog.d("OfflineTaskWorker cid $cid addTaskReturn $addTaskReturn")
        val state = addTaskReturn.first
        val message = addTaskReturn.second
        if (state) {
            //清空缓存
            DataStoreUtil.putData(
                ConfigKeyUtil.CURRENT_OFFLINE_TASK,
                ""
            )
        }
        println("checkOfflineTask $message")
        toast(message, a, cid)
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
    private fun toast(message: String, urlList: List<String>, cid: String) {
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

        val intent = Intent(this.applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        //flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        if (message.contains("任务添加失败")) {
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
        } else {
            intent.action = "jump"
            intent.putExtra("cid", cid)
            notification.setContentText(message)
        }

        val pendingIntent = PendingIntent.getActivity(
            this.applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notification.setContentIntent(pendingIntent)
        notificationManager.notify(notificationId, notification.build())
    }

}