package github.zerorooot.nap511.repository

import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.JsonParser
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.CreateFolderMessage
import github.zerorooot.nap511.bean.FileInfo
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.service.FileService
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.Sha1Util
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.StringJoiner
import kotlin.math.log10
import kotlin.math.pow

class FileRepository(private val cookie: String) {
    companion object {
        @Volatile
        private var INSTANCE: FileRepository? = null
        fun getInstance(cookie: String): FileRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FileRepository(cookie).also { INSTANCE = it }
            }
        }
    }

    private val fileService: FileService by lazy {
        FileService.getInstance(cookie)
    }

    suspend fun getFileInfo(cid: String): FileInfo {
        return fileService.getFileInfo(cid)
    }

    suspend fun createFolder(
        pid: String, folderName: String
    ): CreateFolderMessage {
        return fileService.createFolder(pid, folderName)
    }

    /**
     * 根据文件名，创建一个去除后缀的文件夹，如果文件夹存在则返回当前cid,不存在则返回文件夹cid
     */
    suspend fun createFolderAndReturnCid(cid: String, fileName: String): String {
        val unzipFolderName = fileName.substring(0, fileName.length - 4)
        val createFolderMessage = createFolder(
            cid, unzipFolderName
        )
        return createFolderMessage.let { if (it.cid == "") cid else it.cid }
    }

    suspend fun delete(pid: String, fid: String): BaseReturnMessage {
        return fileService.delete(pid, fid)
    }

    suspend fun rename(renameBean: RequestBody): BaseReturnMessage {
        return fileService.rename(renameBean)
    }

    suspend fun unzipFile(
        pickCode: String,
        zipFileCid: String,
        files: List<String>?,
        dirs: List<String>?,
        showToast: Boolean = true
    ): Pair<Boolean, String> {
        var state = true
        val unzipFile = fileService.unzipFile(pickCode, zipFileCid, files, dirs)
        XLog.d("unzipFile JsonElement $unzipFile")
        val jsonObject = unzipFile.asJsonObject
        //todo 解压失败时处理 unzipFile {"state":false,"message":"压缩包已损坏，无法解压","code":51005,"data":[]}
        //{"state":false,"message":"参数错误。","code":990002,"data":[]}
        state = jsonObject.get("state").asBoolean
        var message = if (state) {
            "后台解压中～"
        } else {
//            "解压失败～${jsonObject.get("message").asString}"
            jsonObject.get("message").asString
        }
        //当解压失败时，返回
        if (!state) {
            return Pair(false, message)
        }
        //解压
        val extractId = jsonObject.getAsJsonObject("data").get("extract_id").asLong

        if (showToast) {
            App.instance.toast(message)
        }

        // {"state":true,"message":"","code":"","data":{"extract_id":"id","to_pid":"pid","percent":100}}

        for (i in 1..20) {
            val json = fileService.unzipFileProcess(extractId)
            XLog.d("unzipFile process $i $json")
            state = json.get("state").asBoolean
            if (!state) {
                message = json.get("message").asString
                break
            }
            val process = json.getAsJsonObject("data").get("percent").asInt
            if (process == 100) {
                state = true
                message = "后台解压完成～"
                break
            }
            Thread.sleep(1000)
        }
        if (showToast) {
            App.instance.toast(message)
        }
        return Pair(state, message)
    }

    suspend fun getZipListFile(
        pickCode: String,
        fileName: String = "",
        paths: String = "文件"
    ): ZipBeanList {
        val data =
            fileService.getZipListFile(pickCode, fileName, paths).getAsJsonObject("data")
        XLog.d("FileRepository.getZipListFile $data")
        val zipBeanList = Gson().fromJson<ZipBeanList>(data, ZipBeanList::class.java)
        val sj = StringJoiner("/")
        data.getAsJsonArray("paths")
            .forEach { sj.add(it.asJsonObject.get("file_name").asString) }
        zipBeanList.pathString = sj.toString()
        zipBeanList.list.forEach { i ->
            if (i.fileCategory == 0) {
                i.fileIco = R.drawable.folder
            } else {
                i.timeString = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                    i.time.toLong() * 1000
                )
                i.sizeString = formatFileSize(i.size.toLong()) + "  "
                when (i.icoString) {
                    "apk" -> i.fileIco = R.drawable.apk
                    "iso" -> i.fileIco = R.drawable.iso
                    "zip" -> i.fileIco = R.drawable.zip
                    "7z" -> i.fileIco = R.drawable.zip
                    "rar" -> i.fileIco = R.drawable.zip
                    "png" -> i.fileIco = R.drawable.png
                    "jpg" -> i.fileIco = R.drawable.png
                    "mp3" -> i.fileIco = R.drawable.mp3
                    "txt" -> i.fileIco = R.drawable.txt
                    "torrent" -> i.fileIco = R.drawable.torrent
                }
            }
        }
        return zipBeanList
    }

    fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        val digitGroups = (log10(sizeInBytes.toDouble()) / log10(1024.0)).toInt()
        return String.format(
            Locale.US,
            "%.2f %s",
            sizeInBytes / 1024.0.pow(digitGroups.toDouble()),
            units[digitGroups]
        )
    }

    suspend fun decryptZip(pickCode: String, secret: String): Boolean {
        //{"state":true,"message":"","code":"","data":{"unzip_status":4}}
        val json = fileService.decryptZip(pickCode, secret)
        XLog.d("decryptZip $json")
        val asInt = json.getAsJsonObject("data").get("unzip_status").asInt
        //4 is success,6 is decrypt error,1 is has been done
        return asInt != 6
    }

    suspend fun isZipFileEncryption(pickCode: String): Boolean {
        val json = fileService.getDecryptZipProcess(pickCode)
        //{"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":4,"progress":100}}}
        XLog.d("checkIsEncryptionZip $json")
        val unzipStatus =
            json.getAsJsonObject("data").getAsJsonObject("extract_status").get("unzip_status").asInt
        //1 is encryption zip file
        return unzipStatus != 4
    }

    /**
     * {"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":4,"progress":100}}}
     */
    suspend fun tryToExtract(pickCode: String): Boolean {
        //{"state":true,"message":"","code":"","data":{"unzip_status":1}}
        val checkDecryptZip = fileService.checkDecryptZip(pickCode)
        val asInt = checkDecryptZip.getAsJsonObject("data").get("unzip_status").asInt
        XLog.d("tryToExtract.checkDecryptZip $checkDecryptZip")
        //之前解压过，密码在115缓存中
        if (asInt == 1) {
            return true
        }
        //6为加密文件
        if (asInt == 6) {
            return false
        }

        //等待解压中
        for (i in 1..20) {
            val json = fileService.getDecryptZipProcess(pickCode)
            val process =
                json.getAsJsonObject("data").getAsJsonObject("extract_status").get("progress").asInt
            XLog.d("tryToExtract zip $i $json")
            if (process == 100) {
                return true
            }
            Thread.sleep(1000)
        }
        return false
    }

    fun getDownloadInputStream(
        pickCode: String, fileId: String
    ): InputStream {
        val sha1Util = Sha1Util()
        val okHttpClient = OkHttpClient()
        val tm = System.currentTimeMillis() / 1000
        val m115Encode = sha1Util.m115_encode(pickCode, tm)
        val map = FormBody.Builder().add("data", m115Encode.data).build()

        val request: Request =
            Request.Builder().url("https://proapi.115.com/app/chrome/downurl?t=$tm")
                .addHeader("cookie", cookie)
                .addHeader("Content-Type", "application/x-www-form-urlencoded").addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
                ).post(map).build()

        val response = okHttpClient.newCall(request).execute()

        val returnJson = JsonParser().parse(response.body.string()).asJsonObject
        val data = returnJson.get("data").asString
        val m115Decode = sha1Util.m115_decode(data, m115Encode.key)


        //{"fileId":{"file_name":"a","file_size":"0","pick_code":"pick_code","url":false}}
        val downloadUrl = JsonParser().parse(m115Decode).asJsonObject.getAsJsonObject(fileId)
            .getAsJsonObject("url").get("url").asString
        XLog.d("downloadUrl $downloadUrl")


        val requestDownload: Request =
            Request.Builder().url(downloadUrl).addHeader("cookie", cookie)
                .addHeader("Content-Type", "application/x-www-form-urlencoded").addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
                ).get().build()
        val responseDownload = okHttpClient.newCall(requestDownload).execute();
        val body = responseDownload.body.byteStream()
        return body
    }
}