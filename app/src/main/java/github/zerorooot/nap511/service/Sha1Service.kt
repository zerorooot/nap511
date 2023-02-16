package github.zerorooot.nap511.service


import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.gson.JsonParser
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileBeanDownload
import github.zerorooot.nap511.util.Sha1Util
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response


class Sha1Service : Service() {
    private val sha1Util = Sha1Util()
    private val okHttpClient = OkHttpClient()
    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fileBeanList = intent!!.getParcelableArrayListExtra<FileBean>("list")!!
        val cookie = intent.getStringExtra("cookie")!!
        Thread{
            val downloadUrlAndCookie = getDownloadUrlAndCookie(fileBeanList, cookie)
        }.start()


        return super.onStartCommand(intent, flags, startId)
    }


    private fun getHash() {

    }

    private fun getDownloadUrlAndCookie(
        fileBeanList: ArrayList<FileBean>,
        cookie: String
    ): ArrayList<FileBeanDownload> {
        val list = arrayListOf<FileBeanDownload>()
        fileBeanList.forEachIndexed { index, fileBean ->
            val response = getDownloadUrlAndCookie(fileBean.pickCode,  cookie)
            val returnJson = JsonParser().parse(response.body?.string()).asJsonObject
            val downloadUrl =
                returnJson.getAsJsonObject(fileBean.fileId).getAsJsonObject("url").get("url").asString
            println(response.headers.toString())

            val newCookie = response.headers["Set-Cookie"]!!
            list.add(FileBeanDownload(newCookie, downloadUrl))

        }
        return list
    }

    private fun getDownloadUrlAndCookie(
        pickCode: String,
        cookie: String
    ): Response {
        val json = "{\"pickcode\":\"${pickCode}\"}"
        val tm = System.currentTimeMillis() / 1000
        val m115Encode = sha1Util.m115_encode(json, tm)
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

        return okHttpClient.newCall(request).execute()
    }
}