package github.zerorooot.nap511.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.worker.OfflineTaskWorker
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        val dataUri: Uri? = intent?.data
        if (dataUri == null && intent.action != Intent.ACTION_PROCESS_TEXT) {
            XLog.d("OfflineTaskActivity 未接收到任何链接数据 intent: $intent")
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

                    XLog.d("OfflineTaskActivity $tag urlList: $urlList")

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
                        val stringJoiner = StringJoiner("\n")
                        currentOfflineTaskList.toSet().forEach { stringJoiner.add(it) }
                        //写入缓存
                        DataStoreUtil.putDataSuspend(
                            ConfigKeyUtil.CURRENT_OFFLINE_TASK,
                            stringJoiner.toString()
                        )
                        App.instance.toast("已添加 ${currentOfflineTaskList.size} 个链接，${offlineTime}分钟后开始离线下载")
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

    @SuppressLint("EnqueueWork")
    private fun addOfflineTaskByTime(
        currentOfflineTaskList: List<String>,
        offlineTime: Long
    ) {
        val listType = object : TypeToken<List<String?>?>() {}.type
        val list = Gson().toJson(currentOfflineTaskList, listType)
        val data: Data = Data.Builder().putString("list", list)
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


