package github.zerorooot.nap511.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.MainActivity
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.SignBean
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigUtil
import github.zerorooot.nap511.util.DataStoreUtil
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.StringJoiner


class OfflineTaskActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_PROCESS_TEXT) {
            val urlList = (intent.dataString ?: run {
                intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            } ?: run { "" }).split("\n").filter { i ->
                i.startsWith("http", true) || i.startsWith(
                    "ftp", true
                ) || i.startsWith("magnet", true) || i.startsWith("ed2k", true)
            }.toList()
//            App.instance.toast(urlList.toString())
            val listType = object : TypeToken<List<String?>?>() {}.type
            val list = Gson().toJson(urlList, listType)
            println(list)
            if (urlList.isNotEmpty()) {
                App.instance.toast("${urlList.size} 个链接添加中......")
                val data: Data =
                    Data.Builder().putString("cookie", App.cookie).putString("list", list).build()
                val request: OneTimeWorkRequest =
                    OneTimeWorkRequest.Builder(OfflineTaskWorker::class.java).setInputData(data)
                        .build()
                WorkManager.getInstance(applicationContext).enqueue(request)
            } else {
                App.instance.toast("仅支持以http、ftp、magnet、ed2k开头的链接")
            }
        }
        finishAndRemoveTask()
    }
}


class OfflineTaskWorker(
    appContext: Context, workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    private val okHttpClient = OkHttpClient()
    override fun doWork(): Result {
        val cookie: String = inputData.getString("cookie").toString()
        val listType = object : TypeToken<List<String?>?>() {}.type
        val a: List<String> = Gson().fromJson(inputData.getString("list").toString(), listType)
        val addTaskData = addTask(a, cookie)
        val message = addTaskData.getString("return").toString()
        println(message)
        toast(message)
        return Result.success(addTaskData);
    }


    @SuppressLint("WrongConstant")
    private fun toast(message: String) {
        //渠道Id
        val channelId = "toast"
        //渠道名
        val channelName = "离线下载结果"
        //渠道重要级
        val importance = NotificationManagerCompat.IMPORTANCE_MAX
        //通知Id
        val notificationId = 1

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
        if (message.contains("请验证账号")) {
            val intent = Intent(this.applicationContext, MainActivity::class.java)
            intent.action = "jump"
            val pendingIntent = PendingIntent.getActivity(
                this.applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
            notification.setContentText("$message。点我跳转验证账号页面")
            notification.setContentIntent(pendingIntent)
        }else{
            notification.setContentText(message)
        }

        notificationManager.notify(notificationId, notification.build())
    }

    private fun addTask(urlList: List<String>, cookie: String): Data {
        val resultMessage = StringJoiner("\n")
        val cid = DataStoreUtil.getData(ConfigUtil.defaultOfflineCid, "")
        val downloadPath = setDownloadPath(cid, cookie)
        if (!downloadPath.state) {
            resultMessage.add("设置离线位置失败，默认保存到\"云下载\"目录")
        }
        val map = HashMap<String, String>()
        map["savepath"] = ""
        map["wp_path_id"] = cid
        map["uid"] = DataStoreUtil.getData(ConfigUtil.uid, "")
        map["sign"] = getSign(cookie).sign
        map["time"] = (System.currentTimeMillis() / 1000).toString()
        urlList.forEachIndexed { index, s ->
            map["url[$index]"] = s
        }
        val addTask = addTask(cookie, map)
        val message = if (addTask.state) {
            "任务添加成功"
        } else {
            "任务添加失败，${addTask.errorMsg}"
        }
        resultMessage.add(message)
        return Data.Builder().putString("return", resultMessage.toString()).build()
    }

    private fun addTask(cookie: String, map: HashMap<String, String>): BaseReturnMessage {
        val builder = FormBody.Builder()
        map.forEach { (t, u) -> builder.add(t, u) }
        val formBody = builder.build()

        val request: Request =
            Request.Builder().url("https://115.com/web/lixian/?ct=lixian&ac=add_task_urls")
                .addHeader("cookie", cookie)
                .addHeader("Content-Type", "application/x-www-form-urlencoded").addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
                ).post(formBody).build()
        val response = okHttpClient.newCall(request).execute()
        return Gson().fromJson(
            response.body?.string(), BaseReturnMessage::class.java
        )
    }

    private fun getSign(cookie: String): SignBean {
        val request: Request = Request.Builder()
            .url("https://115.com?ct=offline&ac=space&_=${System.currentTimeMillis() / 1000}")
            .addHeader("cookie", cookie)
//            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
            ).get().build()
        val response = okHttpClient.newCall(request).execute()
        return Gson().fromJson(
            response.body?.string(), SignBean::class.java
        )
    }

    private fun setDownloadPath(cid: String, cookie: String): BaseReturnMessage {
        val formBody = FormBody.Builder().add("file_id", cid).build()
        val request: Request = Request.Builder().url("https://webapi.115.com/offine/downpath")
            .addHeader("cookie", cookie)
            .addHeader("Content-Type", "application/x-www-form-urlencoded").addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
            ).post(formBody).build()
        val response = okHttpClient.newCall(request).execute()
        return Gson().fromJson(
            response.body?.string(), BaseReturnMessage::class.java
        )
    }
}