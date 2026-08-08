package github.zerorooot.nap511.worker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
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
import github.zerorooot.nap511.util.DialogEvent
import github.zerorooot.nap511.util.DialogEventBus
import java.util.StringJoiner

class OfflineTaskWorker(
    appContext: Context, workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val fileRepository: FileRepository by lazy {
        FileRepository.getInstance(App.cookie)
    }

    companion object {
        const val PROGRESS_NOTIFICATION_ID = 2001
        const val COMPLETE_NOTIFICATION_ID = 2002
        const val CHANNEL_ID = "file_download_channel"
    }

    override suspend fun doWork(): Result {
        val listType = object : TypeToken<List<String?>?>() {}.type
        val a: List<String> = Gson().fromJson(inputData.getString("list").toString(), listType)
        if (a.isEmpty()) {
            return Result.failure()
        }
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            // Android 12+ 在极少数后台受限情况下启动前台服务可能失败
            return Result.failure()
        }


        val cid = DataStoreUtil.getDataSuspend(ConfigKeyUtil.DEFAULT_OFFLINE_CID, "")
        val addTaskReturn = fileRepository.addOfflineTask(a, cid) {}

        XLog.d("OfflineTaskWorker cid $cid addTaskReturn $addTaskReturn task size=${a.size} currentOfflineTask:\n $a")

        val state = addTaskReturn.first
        val message = addTaskReturn.second
        if (state) {
            //清空缓存
            DataStoreUtil.putDataSuspend(
                ConfigKeyUtil.CURRENT_OFFLINE_TASK,
                ""
            )
        }
        XLog.d("OfflineTaskWorker checkOfflineTask $message")
        toast(message, a, cid)
        val addTaskData = Data.Builder()
            .putBoolean("state", state)
            .putString("return", message)
            .build()
        return if (state) {
            DialogEventBus.getInstance().emit(DialogEvent.RefreshFileList(cid))
            Result.success(addTaskData);
        } else {
            Result.failure(addTaskData)
        }
    }

    private fun toast(message: String, urlList: List<String>, cid: String) {
        val notificationManager =
            applicationContext.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID).apply {
            setSmallIcon(R.mipmap.ic_launcher)
            setContentTitle("离线下载结果")//标题
            setAutoCancel(true)
//                setContentText(message)
            setDefaults(Notification.DEFAULT_VIBRATE);
            setStyle(NotificationCompat.BigTextStyle().bigText(message))
        }

        val intent = Intent(this.applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Intent.ACTION_VIEW
        }
        //flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        if (message.contains("任务添加失败")) {
            if (message.contains("请验证账号")) {
                intent.data = "nap511://detail/check".toUri()
                notification.setContentText("$message。点我跳转验证账号页面")
            } else {
                val stringJoiner = StringJoiner("\n")
                urlList.forEach { stringJoiner.add(it) }
                intent.data = "nap511://detail/copy?param=$stringJoiner".toUri()
                intent.putExtra("link", stringJoiner.toString())
                notification.setContentText("$message。点我复制链接")
            }
        } else {
            intent.data = "nap511://detail/jump?param=$cid".toUri()
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
        notificationManager.notify(COMPLETE_NOTIFICATION_ID, notification.build())
    }

    // 必须实现：提供前台服务运行时的“下载中”通知
    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("正在下载文件...")
            .setContentText("请保持网络连接")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true) // 前台服务运行中不可滑动消除
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                PROGRESS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("WrongConstant")
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "文件下载通知",
            NotificationManager.IMPORTANCE_MAX
        )
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}