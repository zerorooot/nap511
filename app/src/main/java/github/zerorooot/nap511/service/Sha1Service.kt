package github.zerorooot.nap511.service


import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.Sha1Util
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.concurrent.thread


class Sha1Service : Service() {
    private val okHttpClient = OkHttpClient()

    private val fileRepository: FileRepository by lazy {
        FileRepository.getInstance(App.cookie)
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent!!.getStringExtra(ConfigKeyUtil.COMMAND)
        thread {
            when (command) {
                ConfigKeyUtil.SENT_TO_ARIA2 -> {
                    val fileBeanList =
                        arrayListOf(
                            Gson().fromJson(
                                intent.getStringExtra("list"),
                                FileBean::class.java
                            )
                        )
                    val downloadUrl = getDownloadUrl(fileBeanList)
                    sendToAria2(downloadUrl)
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }


    private fun getDownloadUrl(
        fileBeanList: ArrayList<FileBean>,
    ): ArrayList<String> {
        val list = arrayListOf<String>()
        fileBeanList.forEach { fileBean ->
            val response = fileRepository.getDownloadUrl(fileBean.pickCode, fileBean.fileId)
            list.add(response)
        }
        return list
    }

    /**
     *
    {
    "jsonrpc": "2.0",
    "method": "aria2.addUri",
    "id": 1,
    "params": [
    "token:xxx",
    [
    "xxx"
    ],
    {
    "header": "User-Agent:Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
    }
    ]
    }
     */
    private fun sendToAria2(fileBeanDownloadList: ArrayList<String>) {
        val aria2Token = DataStoreUtil.getData(ConfigKeyUtil.ARIA2_TOKEN, "")
        val aria2Url = DataStoreUtil.getData(
            ConfigKeyUtil.ARIA2_URL,
            ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE
        ) + "?tm=${System.currentTimeMillis()}"

        val paramsJsonArray = JsonArray()
        if (aria2Token != "") {
            paramsJsonArray.add("token:$aria2Token")
        }

        val urlJsonArray = JsonArray()
        fileBeanDownloadList.forEach { i -> urlJsonArray.add(i) }
        paramsJsonArray.add(urlJsonArray)


        val headersJsonObject = JsonObject()
        headersJsonObject.addProperty(
            "header",
            "User-Agent:Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
        )
        paramsJsonArray.add(headersJsonObject)

        val jsonObject = JsonObject()
        jsonObject.addProperty("jsonrpc", "2.0")
        jsonObject.addProperty("method", "aria2.addUri")
        jsonObject.addProperty("id", "nap511")

        jsonObject.add("params", paramsJsonArray)


        val request: Request = Request
            .Builder()
            .url(aria2Url)
            .post(
                jsonObject.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            )
            .build()


        val message = try {
            val response = okHttpClient.newCall(request).execute()
            val bodyJson = JsonParser.parseString(response.body.string()).getAsJsonObject()
            XLog.d("aria2 json $bodyJson")
            if (bodyJson.has("error")) {
                "下载失败，${
                    bodyJson.getAsJsonObject("error").get("message").asString
                }"
            } else {
                "发送成功!"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "下载失败！${e.message}"
        }

        App.instance.toast(message)

    }

}