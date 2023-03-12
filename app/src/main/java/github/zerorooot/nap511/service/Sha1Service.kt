package github.zerorooot.nap511.service


import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.util.ConfigUtil
import github.zerorooot.nap511.util.Sha1Util
import github.zerorooot.nap511.util.SharedPreferencesUtil
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody


class Sha1Service : Service() {
    private val sha1Util = Sha1Util()
    private val okHttpClient = OkHttpClient()
    private val sharedPreferencesUtil by lazy {
        SharedPreferencesUtil(application)
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fileBeanList = intent!!.getParcelableArrayListExtra("list", FileBean::class.java)!!
        val cookie = intent.getStringExtra("cookie")!!
        Thread {
            val downloadUrlAndCookie = getDownloadUrl(fileBeanList, cookie)
            sendToAria2(downloadUrlAndCookie)
        }.start()


        return super.onStartCommand(intent, flags, startId)
    }


    private fun getHash() {

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
        val aria2Token = sharedPreferencesUtil.get(ConfigUtil.aria2Token)
        val aria2Url = sharedPreferencesUtil.get(
            ConfigUtil.aria2Url,
            ConfigUtil.aria2UrldefValue
        ) + "?tm=${System.currentTimeMillis()}"

        val paramsJsonArray = JsonArray()
        if (aria2Token != null) {
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
}