package github.zerorooot.nap511.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.google.gson.Gson
import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.InitUploadBean
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigUtil
import github.zerorooot.nap511.util.DataStoreUtil
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream


class TorrentTaskActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val torrentFile = fileFromContentUri(this, intent.data!!)
            val uid = App.uid
            val defaultOfflineCid = DataStoreUtil.getData(ConfigUtil.defaultOfflineCid, "0")
            initUpload(torrentFile, App.cookie, uid, defaultOfflineCid)
        }
        moveTaskToBack(true);
        finishAndRemoveTask()
    }


    private fun initUpload(torrentFile: File, cookie: String, uid: String, target: String) {
        val url = "https://uplb.115.com/3.0/sampleinitupload.php"
        val postBody =
            "userid=$uid&filename=${torrentFile.name}&filesize=${torrentFile.length()}&target=U_1_$target".toRequestBody()

        val okHttpClient = OkHttpClient()
        val request: Request = Request.Builder().url(url)
            .addHeader("cookie", cookie)
            .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
            ).post(postBody).build()
        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: okio.IOException) {
                TODO("Not yet implemented")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body
                val string = body.string()
                val initUploadBean = Gson().fromJson(
                    string, InitUploadBean::class.java
                )
//                println(initUploadBean)

                val client = OkHttpClient()
                val requestBody: RequestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("name", torrentFile.name)
                    .addFormDataPart("key", initUploadBean.key)
                    .addFormDataPart("policy", initUploadBean.policy)
                    .addFormDataPart("OSSAccessKeyId", initUploadBean.oSSAccessKeyId)
                    .addFormDataPart("success_action_status", "200")
                    .addFormDataPart("callback", initUploadBean.callback)
                    .addFormDataPart("signature", initUploadBean.signature)
                    .addFormDataPart(
                        "file",
                        torrentFile.name,
                        torrentFile.asRequestBody("application/x-bittorrent".toMediaType())
                    )
                    .build()
                val uploadRequest: Request =
                    Request.Builder().url(initUploadBean.host)
                        .addHeader("origin", "https://115.com")
                        .addHeader("referer", "https://115.com")
                        .addHeader("cookie", cookie)
                        .addHeader(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
                        ).post(requestBody).build()

                client.newCall(uploadRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: okio.IOException) {
                        TODO("Not yet implemented")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val uploadBody = response.body.string()

                        /**
                         * {
                         *     "state": true,
                         *     "message": "",
                         *     "code": 0,
                         *     "data": {
                         *         "aid": xxx,
                         *         "cid": "0",
                         *         "file_name": "xxxx",
                         *         "file_ptime": xxx,
                         *         "file_status": 1,
                         *         "file_id": "xxx",
                         *         "file_size": "229771",
                         *         "pick_code": "xxx",
                         *         "sha1": "xxx",
                         *         "sp": 1,
                         *         "file_type": 188,
                         *         "is_video": 0
                         *     }
                         * }
                         */
                        val uploadJson = Gson().fromJson(
                            uploadBody, BaseReturnMessage::class.java
                        )
                        val message = if (uploadJson.state) {
                            "上传种子文件成功,种子文件保存到默认离线位置中"
                        } else {
                            "上传种子文件失败,${uploadJson.message}"
                        }
                        App.instance.toast(message)

                    }

                })


            }
        })


    }


    private fun fileFromContentUri(context: Context, contentUri: Uri): File {
        val fileName = File(contentUri.path!!).name
        val tempFile = File(context.cacheDir, fileName)
        if (tempFile.exists()) {
            tempFile.delete()
        }
        tempFile.createNewFile()
        try {
            val oStream = FileOutputStream(tempFile)
            val inputStream = context.contentResolver.openInputStream(contentUri)
            inputStream?.let {
                copy(inputStream, oStream)
            }
            oStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return tempFile
    }

    @Throws(java.io.IOException::class)
    private fun copy(source: InputStream, target: OutputStream) {
        val buf = ByteArray(8192)
        var length: Int
        while (source.read(buf).also { length = it } > 0) {
            target.write(buf, 0, length)
        }
    }
}

