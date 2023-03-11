package github.zerorooot.nap511.service


import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import com.google.gson.JsonParser
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileBeanDownload
import github.zerorooot.nap511.util.Sha1Util
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request


class Sha1Service : Service() {
    private val sha1Util = Sha1Util()
    private val okHttpClient = OkHttpClient()
    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fileBeanList = intent!!.getParcelableArrayListExtra("list", FileBean::class.java)!!
        val cookie = intent.getStringExtra("cookie")!!
        Thread {
            val downloadUrlAndCookie = getDownloadUrlAndCookie(fileBeanList, cookie)
            println(downloadUrlAndCookie)
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
        fileBeanList.forEach { fileBean ->
            val response = getDownloadUrlAndCookie(fileBean.pickCode, fileBean.fileId, cookie)
            list.add(response)
        }
        return list
    }

    private fun getDownloadUrlAndCookie(
        pickCode: String,
        fileId: String,
        cookie: String
    ): FileBeanDownload {
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

        val downloadUrl = JsonParser().parse(m115Decode).asJsonObject.getAsJsonObject(fileId)
            .getAsJsonObject("url").get("url").asString

        val newCookie = response.headers["Set-Cookie"]!!.replace(" ", "")
        return FileBeanDownload(newCookie, downloadUrl)

    }
}