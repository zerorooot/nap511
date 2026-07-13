package github.zerorooot.nap511.repository

import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.JsonObject
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.CreateFolderMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileInfo
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.ImageDate
import github.zerorooot.nap511.bean.OfflineInfo
import github.zerorooot.nap511.bean.QuotaBean
import github.zerorooot.nap511.bean.SignBean
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.bean.ZipStatus
import github.zerorooot.nap511.service.FileService
import github.zerorooot.nap511.service.OfflineService
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.Sha1Util
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
import kotlin.time.Duration.Companion.milliseconds

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

    private val offlineService: OfflineService by lazy {
        OfflineService.getInstance(cookie)
    }


    //-----------------离线下载相关--------------------------------
    suspend fun getOfflineSign(): SignBean {
        return offlineService.getSign()
    }

    suspend fun getOfflineTaskList(uid: String = "", sign: String = ""): OfflineInfo {
        return offlineService.taskList(uid, sign)
    }

    suspend fun getOfflineTorrentTaskList(
        sha1: String = "",
        sign: String = "",
        uid: String = "",
    ): TorrentFileBean {
        return offlineService.getTorrentTaskList(sha1, sign, uid)
    }

    suspend fun addOfflineTorrentTask(
        infoHash: String = "",
        wanted: String = "",
        savePath: String = "",
        uid: String = "",
        sign: String,
    ): BaseReturnMessage {
        return offlineService.addTorrentTask(infoHash, wanted, savePath, uid, sign)
    }

    suspend fun quota(): QuotaBean {
        return offlineService.quota()
    }

    suspend fun addOfflineTask(
        list: List<String>,
        currentCid: String,
        handle: (Boolean) -> Unit
    ): Pair<Boolean, String> {
        val downloadPath = setDownloadPath(currentCid)
        XLog.d("add task downloadPath $downloadPath")
        if (!downloadPath.state) {
            App.instance.toast("设置离线位置失败，默认保存到\"云下载\"目录")
        }

        val map = HashMap<String, String>()
        map["savepath"] = ""
        map["wp_path_id"] = currentCid
        map["uid"] = App.uid
        map["sign"] = getOfflineSign().sign
        map["time"] = (System.currentTimeMillis() / 1000).toString()
        list.forEachIndexed { index, s ->
            map["url[$index]"] = s
        }
        val addTask = offlineService.addTask(map)
        XLog.d("add task addTask $addTask")
        val message = if (addTask.state) {
            "任务添加成功"
        } else {
            if (addTask.errorMsg.contains("请验证账号")) {
                handle.invoke(true)
                //   App.selectedItem = ConfigKeyUtil.VERIFY_MAGNET_LINK_ACCOUNT
            }
            //把失败的离线链接保存起来
            val currentOfflineTaskList =
                DataStoreUtil.getData(ConfigKeyUtil.CURRENT_OFFLINE_TASK, "")
                    .split("\n")
                    .filter { i -> i != "" && i != " " }
                    .toSet()
                    .toMutableList()
            currentOfflineTaskList.addAll(list)
            val stringJoiner = StringJoiner("\n")
            currentOfflineTaskList.toSet().forEach { stringJoiner.add(it) }
            //写入缓存
            DataStoreUtil.putData(
                ConfigKeyUtil.CURRENT_OFFLINE_TASK,
                stringJoiner.toString()
            )
            "任务添加失败，${addTask.errorMsg}"
        }

        App.instance.toast(message)
        return Pair(addTask.state, message)
    }

    suspend fun deleteOfflineTask(body: HashMap<String, String>): BaseReturnMessage {
        return offlineService.deleteTask(body)
    }

    suspend fun clearOfflineError(): BaseReturnMessage {
        return offlineService.clearError()
    }

    suspend fun clearOfflineFinish(): BaseReturnMessage {
        return offlineService.clearFinish()
    }

    //-----------------离线下载结束--------------------------------

    suspend fun getFiles(
        cid: String,
        showDir: Int = 1,
        aid: Int = 1,
        asc: Int = 1,
        order: String = "file_name",
        limit: Int = App.requestLimitCount
    ): FilesBean {
        return fileService.getFiles(cid, showDir, aid, asc, order, limit)
    }

    suspend fun remainingSpace(countSpaceNum: Int = 1): JsonObject {
        return fileService.remainingSpace(countSpaceNum)
    }

    suspend fun order(body: Map<String, String>): BaseReturnMessage {
        return fileService.order(body)
    }

    suspend fun deleteMultiple(data: Map<String, String>): BaseReturnMessage {
        return fileService.deleteMultiple(data)
    }

    suspend fun search(
        cid: String,
        searchValue: String,
        aid: Int = 1,
        asc: Int = 0,
        limit: Int = 999
    ): FilesBean {
        return fileService.search(cid, searchValue, aid, asc, limit)
    }

    suspend fun image(pickCode: String, current: Long): ImageDate {
        return fileService.image(pickCode, current)
    }


    suspend fun videoHistory(body: Map<String, String>): BaseReturnMessage {
        return fileService.videoHistory(body)
    }

    suspend fun video(pickCode: String): VideoInfoBean {
        return fileService.video(pickCode)

    }

    suspend fun getFileInfo(cid: String): FileInfo {
        return fileService.getFileInfo(cid)
    }

    suspend fun createFolder(
        pid: String, folderName: String
    ): CreateFolderMessage {
        return fileService.createFolder(pid, folderName)
    }

    suspend fun removeFile(currentCid: String, removeFileList: List<FileBean>): BaseReturnMessage {
        val hashMapOf = hashMapOf<String, String>()
        hashMapOf["pid"] = currentCid
        removeFileList.forEachIndexed { index, fileBean ->
            hashMapOf["fid[$index]"] = fileBean.fileId
        }
        return fileService.move(hashMapOf)
    }

    suspend fun setDownloadPath(cid: String): BaseReturnMessage {
        return fileService.setDownloadPath(cid)
    }

    /**
     * 根据文件名，创建一个去除后缀的文件夹，如果文件夹存在则返回当前cid,不存在则返回文件夹cid
     */
    suspend fun createFolderAndReturnCid(cid: String, fileName: String): String {
        val unzipFolderName = fileName.substringBeforeLast(".")
        val createFolderMessage = createFolder(
            cid, unzipFolderName
        )
        val returnCid = createFolderMessage.let { if (it.cid == "") cid else it.cid }
        XLog.d("createFolderAndReturnCid $createFolderMessage  inputCid:$cid returnCid:$returnCid ")
        return returnCid
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
        unzipFolderName: String = "",
        showToast: Boolean = true
    ): Pair<Boolean, String> {
        var state: Boolean
        val unzipFile = fileService.unzipFile(pickCode, zipFileCid, files, dirs)
        XLog.d("unzipFile JsonElement $unzipFile")
        //解压失败时处理 unzipFile {"state":false,"message":"压缩包已损坏，无法解压","code":51005,"data":[]}
        //{"state":false,"message":"参数错误。","code":990002,"data":[]}
        state = unzipFile.state
        var message = if (state) {
            "后台解压中～"
        } else {
//            "解压失败～${jsonObject.get("message").asString}"
            unzipFile.message
        }
        //当解压失败时，返回
        if (!state) {
            return Pair(false, message)
        }
        //解压
        val extractId = unzipFile.data.extractId.toLong()

        if (showToast) {
            App.instance.toast(message)
        }

        // {"state":true,"message":"","code":"","data":{"extract_id":"id","to_pid":"pid","percent":100}}

        for (i in 1..100) {
            val json = fileService.unzipFileProcess(extractId)
            XLog.d("unzipFile process $i $json")
            state = json.state
            if (!state) {
                message = json.message
                break
            }
            val process = json.data.percent
            if (process == 100) {
                state = true
                message = "${unzipFolderName}后台解压完成～"
                break
            }
            delay(1000.milliseconds)
        }
        if (showToast) {
            App.instance.toast(message)
        }
        return Pair(state, message)
    }

    /**
     * {"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":4,"progress":100}}}
     */
    suspend fun tryToExtract(pickCode: String): Boolean {
        //{"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":4,"progress":100}}}
        val checkDecryptZip = fileService.getDecryptZipProcess(pickCode)
        val asInt = checkDecryptZip.data.extractStatus.unzipStatus
        XLog.d("Get files/push_extract tryToExtract.checkDecryptZip $checkDecryptZip")
        //之前解压过，密码在115缓存中
        if (asInt == 1) {
            return true
        }
        //6为加密文件
        if (asInt == 6) {
            return false
        }

        //等待解压中
        for (i in 1..100) {
            val json = fileService.getDecryptZipProcess(pickCode)
            val process = json.data.extractStatus.progress
            XLog.d("tryToExtract zip $i $json")
            if (process == 100) {
                return true
            }
            delay(1000.milliseconds)
        }
        return false
    }

    suspend fun getZipListFile(
        pickCode: String,
        fileName: String = "",
        paths: String = "文件"
    ): ZipBeanList {
        val zipListFile = fileService.getZipListFile(pickCode, fileName, paths)
        val data = zipListFile.getAsJsonObject("data")
        XLog.d("FileRepository.getZipListFile $zipListFile")

        val zipBeanList = Gson().fromJson(data, ZipBeanList::class.java)
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
                i.sizeString = formatFileSize(i.size) + "  "
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
        XLog.d("Post files/push_extract decryptZip $json")
        val asInt = json.data.unzipStatus
        //4 is success,6 is decrypt error,1 is having been done
        return asInt != 6
    }


    suspend fun checkZipStatus(pickCode: String): ZipStatus {
        return try {
            val response = fileService.getDecryptZipProcess(pickCode)
            //加密压缩包 {"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":6,"progress":88}}}
            //正常无加密压缩包 {"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":4,"progress":100}}}
            //官网显示正在服务器解压 {"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":1,"progress":5}}}
            //官网显示正在服务器解压,意味着加密或者非加密文件{"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":0,"progress":0}}}
            //官网显示正在服务器解压,意味着加密或者非加密文件{"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":2,"progress":30}}}
            XLog.d("Get files/push_extract checkZipStatus $response")
            if (!response.state) {
                ZipStatus.UnsupportedOrError(response.message)
            } else {
                val status = response.data.extractStatus
                    ?: return ZipStatus.UnsupportedOrError("提取状态数据缺失")
                var unzipStatus = status.unzipStatus

                // 2. 特殊状态码二次校验
                if (unzipStatus == 0 || unzipStatus == 2) {
                    val encryptResponse = fileService.checkEncryptionStatus(pickCode)
                    XLog.d("checkEncryptionStatus: $encryptResponse")

                    if (!encryptResponse.state) {
                        return ZipStatus.UnsupportedOrError(encryptResponse.message)
                    }
                    unzipStatus = encryptResponse.data.unzipStatus
                }

                // 3. 映射到密封类
                when (unzipStatus) {
                    1 -> ZipStatus.Loading(status.progress)
                    4 -> ZipStatus.Normal
                    6 -> ZipStatus.Encrypted
                    else -> ZipStatus.UnsupportedOrError("未知状态: $response")
                }
            }
        } catch (e: CancellationException) {
            XLog.d("checkZipStatus 任务被取消 $e")
            ZipStatus.UnsupportedOrError("任务被取消")
        } catch (e: Exception) {
            // 4. 捕获网络异常、Json语法解析异常等，确保不崩溃
            XLog.e("checkZipStatus 发生异常", e)
            ZipStatus.UnsupportedOrError("网络或系统异常: ${e.localizedMessage}")
        }
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

        val returnJson = Gson().fromJson(response.body.string(), JsonObject::class.java)
        val data = returnJson.get("data").asString
        val m115Decode = sha1Util.m115_decode(data, m115Encode.key)


        //{"fileId":{"file_name":"a","file_size":"0","pick_code":"pick_code","url":false}}
        val downloadUrl =
            Gson().fromJson(m115Decode, JsonObject::class.java).getAsJsonObject(fileId)
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