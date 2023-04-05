package github.zerorooot.nap511.service


import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.util.ConfigUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.Sha1Util
//import github.zerorooot.nap511.util.SharedPreferencesUtil
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.StringJoiner
import kotlin.concurrent.thread


class Sha1Service : Service() {
    private val sha1Util = Sha1Util()
    private val okHttpClient = OkHttpClient()
//    private val sharedPreferencesUtil by lazy {
//        SharedPreferencesUtil(application)
//    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent!!.getStringExtra(ConfigUtil.command)
        val fileBeanList =
            intent.getParcelableArrayListExtra("list", FileBean::class.java)!!
        val cookie = intent.getStringExtra("cookie")!!

        thread {
            val downloadUrl = getDownloadUrl(fileBeanList, cookie)
            when (command) {
                ConfigUtil.sentToAria2 -> {
                    sendToAria2(downloadUrl)
                }
                ConfigUtil.getSha1 -> {
                    get115Sha1(downloadUrl, fileBeanList)
                }
            }
        }



        return super.onStartCommand(intent, flags, startId)
    }


    private fun get115Sha1(downloadUrl: ArrayList<String>, fileBeanList: ArrayList<FileBean>) {
        val sb = StringJoiner("\n")
        // 115://文件名|大小|sha1|preid
        downloadUrl.forEachIndexed { index, s ->
            val preId = getPreId(s)
            val fileBean = fileBeanList[index]
            val sha = "115://${fileBean.name}|${fileBean.size}|${fileBean.sha1}|${preId}"
            sb.add(sha)
        }
        val clipboard = ContextCompat.getSystemService(this, ClipboardManager::class.java)
        val clip = ClipData.newPlainText("115sha1", sb.toString())
        clipboard?.setPrimaryClip(clip)

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, "获取SHA1成功，已输出至剪贴板", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getDownloadUrl(
        fileBeanList: ArrayList<FileBean>,
        cookie: String
    ): ArrayList<String> {
        val list = arrayListOf<String>()
        fileBeanList.forEach { fileBean ->
            val response =
                getDownloadUrl(fileBean.pickCode, fileBean.fileId, cookie)
            list.add(response)
        }
        return list
    }

    private fun getDownloadUrl(
        pickCode: String,
        fileId: String,
        cookie: String
    ): String {
        val tm = System.currentTimeMillis() / 1000
        val m115Encode = sha1Util.m115_encode(pickCode, tm)
        val map = FormBody.Builder().add("data", m115Encode.data).build()

        val request: Request = Request
            .Builder()
            .url("https://proapi.115.com/app/chrome/downurl?t=$tm")
            .addHeader("cookie", cookie)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
            )
            .post(map)
            .build()

        val response = okHttpClient.newCall(request).execute()

        val returnJson = JsonParser().parse(response.body?.string()).asJsonObject
        val data = returnJson.get("data").asString
        val m115Decode = sha1Util.m115_decode(data, m115Encode.key)


        //{"fileId":{"file_name":"a","file_size":"0","pick_code":"pick_code","url":false}}

        return JsonParser().parse(m115Decode).asJsonObject.getAsJsonObject(fileId)
            .getAsJsonObject("url").get("url").asString

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
//        val aria2Token = sharedPreferencesUtil.get(ConfigUtil.aria2Token)
//        val aria2Url = sharedPreferencesUtil.get(
//            ConfigUtil.aria2Url,
//            ConfigUtil.aria2UrldefValue
//        ) + "?tm=${System.currentTimeMillis()}"
        val aria2Token = DataStoreUtil.getData(ConfigUtil.aria2Token, "")
        val aria2Url = DataStoreUtil.getData(
            ConfigUtil.aria2Url,
            ConfigUtil.aria2UrldefValue
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
        val response = okHttpClient.newCall(request).execute()

        val bodyJson = JsonParser().parse(response.body!!.string()).asJsonObject

        Handler(Looper.getMainLooper()).post {
            if (bodyJson.has("error")) {
                Toast.makeText(
                    application,
                    "下载失败，${bodyJson.getAsJsonObject("error").get("message").asString}",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(application, "发送成功!", Toast.LENGTH_SHORT).show()
            }
        }

    }


    private fun download(downloadUrl: String, cookie: String) {
        val request: Request = Request
            .Builder().url(downloadUrl)
            .addHeader("cookie", cookie)
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
            )
            .get()
            .build()
        val response = okHttpClient.newCall(request).execute()
        println(response.body!!.string())
    }

    private fun getPreId(downloadUrl: String): String {
        val request: Request = Request
            .Builder().url(downloadUrl)
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
            )
            .addHeader("Range", "bytes=0-131072")
            .get()
            .build()
        val response = okHttpClient.newCall(request).execute()
        val asUByteArray = response.body!!.source().readByteArray(128 * 1024)

        val digest = MessageDigest.getInstance("SHA-1").digest(asUByteArray)
        val sb = StringBuilder()
        for (b in digest) {
            sb.append(String.format("%02X", b))
        }

        return sb.toString()
    }
}