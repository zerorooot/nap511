package github.zerorooot.nap511.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.worker.OfflineTaskWorker
import java.util.StringJoiner
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class OfflineTaskActivity : ComponentActivity() {
    // 40位十六进制哈希正则 (BTih v1 标准)
    private val HEX_40_PATTERN = Pattern.compile("^[0-9a-fA-F]{40}$")

    // 32位Base32哈希正则 (早期或简短版磁力链标准)
    private val BASE32_32_PATTERN = Pattern.compile("^[a-zA-Z2-7]{32}$")

    @SuppressLint("EnqueueWork")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_PROCESS_TEXT) {
            val urlList = (intent.dataString ?: run {
                intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            } ?: run { "" }).split("\n").map { i ->
                //支持复制无头磁力链接
                val a = i.replace(Regex("&dn=.*"), "").trim()
                if (HEX_40_PATTERN.matcher(a).matches() || BASE32_32_PATTERN.matcher(a).matches()) {
                    "magnet:?xt=urn:btih:$a"
                } else {
                    a
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

                addOfflineTaskByTime(currentOfflineTaskList.toList())

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


