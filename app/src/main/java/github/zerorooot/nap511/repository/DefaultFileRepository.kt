package github.zerorooot.nap511.repository

import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.Base115Response
import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.CreateFolderMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileInfo
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.ImageDate
import github.zerorooot.nap511.bean.InitUploadBean
import github.zerorooot.nap511.bean.OfflineInfo
import github.zerorooot.nap511.bean.OfflineListCount
import github.zerorooot.nap511.bean.OfflineTaskType
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.OrderType
import github.zerorooot.nap511.bean.QuotaBean
import github.zerorooot.nap511.bean.RecycleInfo
import github.zerorooot.nap511.bean.SignBean
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.bean.UploadBean
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.bean.ZipBean
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.bean.ZipStatus
import github.zerorooot.nap511.service.FileService
import github.zerorooot.nap511.service.OfflineService
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.crypto.Sha1Util
import github.zerorooot.nap511.util.formatFileSize
import github.zerorooot.nap511.util.network.NetworkClient
import github.zerorooot.nap511.util.network.UserSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.StringJoiner
import kotlin.time.Duration.Companion.milliseconds

/**
 * 生产环境默认的文件数据仓库实现
 * 依赖显式注入的 [FileService] 与 [OfflineService]，内部杜绝任何静态单例调用。
 */
class DefaultFileRepository(
    private val fileService: FileService,
    private val offlineService: OfflineService
) : FileRepository {

    //-----------------离线下载相关--------------------------------
    override suspend fun getOfflineSign(): SignBean {
        return offlineService.getSign()
    }

    override suspend fun getOfflineTaskCount(): OfflineListCount {
        return offlineService.taskCount()
    }

    override suspend fun getOfflineTaskList(
        uid: String, sign: String, page: Int, offlineTaskType: OfflineTaskType
    ): OfflineInfo {
        return offlineService.taskList(uid, sign, page, offlineTaskType.stat)
    }


    override suspend fun getOfflineTorrentTaskList(
        sha1: String,
        sign: String,
        uid: String,
    ): TorrentFileBean {
        return offlineService.getTorrentTaskList(sha1, sign, uid)
    }

    override suspend fun addOfflineTorrentTask(
        infoHash: String,
        wanted: String,
        savePath: String,
        uid: String,
        sign: String,
    ): BaseReturnMessage {
        return offlineService.addTorrentTask(infoHash, wanted, savePath, uid, sign)
    }

    override suspend fun quota(): QuotaBean {
        return offlineService.quota()
    }

    override suspend fun addOfflineTask(
        list: List<String>, currentCid: String, offlinePath: String, handle: (Boolean) -> Unit
    ): Pair<Boolean, String> {
        val downloadPath = setDownloadPath(currentCid)
        XLog.i("add task downloadPath $downloadPath")
        if (!downloadPath.state) {
            return Pair(false, downloadPath.error)
        }
        val quota = offlineService.quota()
        val surplus = quota.surplus - list.size

        val map = HashMap<String, String>()
        map["savepath"] = ""
        map["wp_path_id"] = currentCid
        map["uid"] = UserSessionManager.uid
        map["sign"] = getOfflineSign().sign
        map["time"] = (System.currentTimeMillis() / 1000).toString()
        list.forEachIndexed { index, s ->
            map["url[$index]"] = s
        }
        val addTask = offlineService.addTask(map)
        XLog.d("add task addTask $addTask")
        val message = if (addTask.state) {
            "成功将任务添加到‘${offlinePath}’目录，剩余配额${surplus}个"
        } else {
            if (addTask.errorMsg.contains("请验证账号")) {
                handle.invoke(true)
            }
            "任务添加失败，${addTask.errorMsg}"
        }

        return Pair(addTask.state, message)
    }

    override suspend fun deleteOfflineTask(body: HashMap<String, String>): BaseReturnMessage {
        return offlineService.deleteTask(body)
    }

    override suspend fun clearOfflineError(): BaseReturnMessage {
        return offlineService.clearError()
    }

    override suspend fun clearOfflineFinish(): BaseReturnMessage {
        return offlineService.clearFinish()
    }

    //-----------------离线下载结束--------------------------------

    override suspend fun getFiles(
        cid: String,
        showDir: Int,
        aid: Int,
        asc: Int,
        naturalSort: Int,
        order: String,
        limit: Int,
        format: String,
        offset: Int,
        fcMix: Int,
    ): FilesBean {
        return fileService.getFiles(
            cid,
            showDir,
            aid,
            asc,
            order,
            limit,
            naturalSort,
            format,
            offset,
            fcMix
        )
    }

    override suspend fun remainingSpace(countSpaceNum: Int): JsonObject {
        return fileService.remainingSpace(countSpaceNum)
    }

    override suspend fun order(body: Map<String, String>): BaseReturnMessage {
        return fileService.order(body)
    }

    override suspend fun deleteMultiple(data: Map<String, String>): BaseReturnMessage {
        return executeWithRetry { fileService.deleteMultiple(data) }
    }

    override suspend fun search(
        cid: String,
        searchValue: String,
        aid: Int,
        offset: Int,
        limit: Int
    ): FilesBean {
        return fileService.search(
            cid = cid,
            searchValue = searchValue,
            aid = aid,
            offset = offset,
            limit = limit
        )
    }

    override suspend fun filterFile(
        cid: String,
        type: Int,
        order: String,
        asc: Int,
        offset: Int,
        limit: Int,
        fcMix: Int
    ): FilesBean {
        return fileService.filterFile(
            cid = cid,
            type = type,
            order = order,
            asc = asc,
            offset = offset,
            limit = limit,
            fcMix = fcMix
        )
    }

    override suspend fun starFile(
        offset: Int,
        order: OrderBean,
        fcMix: Int,
        limit: Int,
    ): FilesBean {
        return if (order.orderType == OrderType.STAR) {
            fileService.starFile(
                asc = order.asc,
                offset = 0,
                limit = limit,
            )
        } else {
            fileService.starFile(
                asc = order.asc,
                offset = 0,
                limit = limit,
                order = order.type,
                fcMix = fcMix,
                //加了这个，文件夹置顶就无效了
//                customOrder = 2
            )
        }
    }

    /**
     * 文件加⭐，true 表示加上，false 表示取消
     */
    override suspend fun star(fid: String, star: Boolean): BaseReturnMessage {
        val starString = if (star) "1" else "0"
        return fileService.star(fid, starString)
    }

    /**
     * 文件置顶，true 表示置顶，false 表示取消
     */
    override suspend fun top(fid: String, top: Boolean): BaseReturnMessage {
        val topString = if (top) "1" else "0"
        return fileService.top(fid, topString)
    }

    override suspend fun image(pickCode: String, current: Long): ImageDate {
        return fileService.image(pickCode, current)
    }


    /**
     * 更新并提交视频播放进度至 115 云端历史记录
     *
     * 具备以下特性：
     * 1. 线程切换至 Dispatchers.IO；
     * 2. 包裹在 NonCancellable 中，确保即使外部协程取消，网络请求依然坚持完成；
     * 3. 基础参数与 0 秒过滤保护；
     * 4. 统一日志打印与异常捕获。
     *
     * @param pickCode 视频文件的唯一提取码
     * @param currentDuration 播放进度（秒）
     * @param name 视频文件名（用于调试日志）
     * @return 是否同步成功
     */
    override suspend fun updateVideoHistory(
        pickCode: String,
        currentDuration: Int,
        name: String
    ): Boolean = withContext(Dispatchers.IO + NonCancellable) {
        if (pickCode.isEmpty() || currentDuration <= 5) return@withContext false
        val map = mapOf(
            "op" to "update",
            "pick_code" to pickCode,
            "time" to currentDuration.toString(),
            "category" to "1",
            "format" to "json"
        )
        runCatching {
            val videoHistory = fileService.videoHistory(map)
            if (!videoHistory.state) {
                XLog.e("更新视频时间失败！ name: $name, time: $currentDuration, pickCode: $pickCode, result: $videoHistory")
                false
            } else {
                XLog.d("更新视频时间成功 name: $name, time: $currentDuration, pickCode: $pickCode, result: $videoHistory")
                true
            }
        }.onFailure { e ->
            XLog.e("更新视频时间异常 name: $name, time: $currentDuration, pickCode: $pickCode", e)
        }.getOrDefault(false)
    }

    override suspend fun video(pickCode: String): VideoInfoBean {
        return fileService.video(pickCode)
    }

    override suspend fun getFileInfo(cid: String): FileInfo {
        return fileService.getFileInfo(cid)
    }

    override suspend fun createFolder(
        pid: String, folderName: String
    ): CreateFolderMessage {
        return fileService.createFolder(pid, folderName)
    }

    private suspend fun executeWithRetry(
        maxRetries: Int = 20,
        delayMs: Long = 1000L,
        block: suspend () -> BaseReturnMessage
    ): BaseReturnMessage {
        var retries = 0
        while (true) {
            val res = block()
            if (res.state) {
                return res
            }
            val err = res.error + res.errorMsg + res.message
            if ((err.contains("尚未执行完成") || err.contains("请稍后再试") || err.contains("尚未完成") || err.contains(
                    "操作尚未完成"
                )) && retries < maxRetries
            ) {
                retries++
                delay(delayMs.milliseconds)
                continue
            }
            return res
        }
    }

    /**
     *  @param currentCid 要移动到的目录
     *  @param removeAllFile 要移动的文件
     */
    override suspend fun removeAllFile(
        currentCid: String,
        removeAllFile: List<FileBean>
    ): BaseReturnMessage {
        val hashMapOf = hashMapOf<String, String>()
        hashMapOf["pid"] = currentCid
        removeAllFile.forEachIndexed { index, fileBean ->
            hashMapOf["fid[$index]"] = fileBean.fileId
        }
        return move(hashMapOf)
    }

    override suspend fun setDownloadPath(cid: String): BaseReturnMessage {
        return fileService.setDownloadPath(cid)
    }

    override suspend fun move(body: Map<String, String>): BaseReturnMessage {
        return executeWithRetry { fileService.move(body) }
    }

    override suspend fun recycleList(
        aid: String,
        cid: String,
        offset: String,
        limit: String
    ): RecycleInfo {
        return fileService.recycleList(aid, cid, offset, limit)
    }

    override suspend fun revert(rid: String): BaseReturnMessage {
        return fileService.revert(rid)
    }

    override suspend fun recycleCleanAll(password: String): BaseReturnMessage {
        return fileService.recycleCleanAll(password)
    }

    /**
     * 根据文件名，创建一个去除后缀的文件夹，如果文件夹存在则返回当前cid,不存在则返回文件夹cid
     */
    override suspend fun createFolderAndReturnCid(cid: String, fileName: String): String {
        val unzipFolderName = fileName.substringBeforeLast(".")
        val createFolderMessage = createFolder(
            cid, unzipFolderName
        )
        val returnCid = createFolderMessage.let { if (it.cid == "") cid else it.cid }
        XLog.i("createFolderAndReturnCid $createFolderMessage  inputCid:$cid returnCid:$returnCid ")
        return returnCid
    }

    override suspend fun delete(pid: String, fid: String): BaseReturnMessage {
        return executeWithRetry { fileService.delete(pid, fid) }
    }

    override suspend fun rename(renameBean: RequestBody): BaseReturnMessage {
        return executeWithRetry { fileService.rename(renameBean) }
    }

    override suspend fun unzipFile(
        pickCode: String,
        zipFileCid: String,
        files: List<String>?,
        dirs: List<String>?,
        unzipFolderName: String
    ): Pair<Boolean, String> {
        var state: Boolean
        val unzipFile = fileService.unzipFile(pickCode, zipFileCid, files, dirs)
        XLog.v("unzipFile JsonElement $unzipFile")
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
        val extractId = unzipFile.data?.extractId?.toLongOrNull() ?: return Pair(
            false,
            message.ifEmpty { "获取解压任务ID失败" }
        )

        // {"state":true,"message":"","code":"","data":{"extract_id":"id","to_pid":"pid","percent":100}}

        for (i in 1..50) {
            val json = fileService.unzipFileProcess(extractId)
            XLog.v("unzipFile process $i $json")
            state = json.state
            if (!state) {
                message = json.message
                break
            }
            val process = json.data?.percent ?: 0
            if (process == 100) {
                state = true
                message = "${unzipFolderName}后台解压完成～"
                break
            }
            delay(1000.milliseconds)
        }
        return Pair(state, message)
    }

    /**
     * {"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":4,"progress":100}}}
     */
    override suspend fun tryToExtract(pickCode: String): Boolean {
        //{"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":4,"progress":100}}}
        val checkDecryptZip = fileService.getDecryptZipProcess(pickCode)
        val asInt = checkDecryptZip.data?.extractStatus?.unzipStatus
        XLog.v("Get files/push_extract tryToExtract.checkDecryptZip $checkDecryptZip")
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
            val process = json.data?.extractStatus?.progress
            XLog.v("tryToExtract zip $i $json")
            if (process == 100) {
                return true
            }
            delay(1000.milliseconds)
        }
        return false
    }

    private fun formatZipBean(bean: ZipBean): ZipBean {
        if (bean.fileCategory == 0) {
            return bean.copy(fileIco = R.drawable.folder)
        }
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
            bean.time.toLong() * 1000
        )
        val sizeStr = bean.size.formatFileSize()
        val ico = when (bean.icoString) {
            "apk" -> R.drawable.apk
            "iso" -> R.drawable.iso
            "zip", "7z", "rar" -> R.drawable.zip
            "png", "jpg" -> R.drawable.png
            "mp3" -> R.drawable.mp3
            "txt" -> R.drawable.txt
            "torrent" -> R.drawable.torrent
            else -> R.drawable.other
        }
        return bean.copy(fileIco = ico, timeString = timeStr, sizeString = sizeStr)
    }

    override suspend fun getZipListFile(
        pickCode: String,
        fileName: String,
        paths: String
    ): ZipBeanList {
        val zipListFile = fileService.getZipListFile(pickCode, fileName, paths)
        val data = zipListFile.getAsJsonObject("data")
        XLog.d("FileRepository.getZipListFile $zipListFile")

        val rawZipBeanList = Gson().fromJson(data, ZipBeanList::class.java) ?: ZipBeanList()
        val sj = StringJoiner("/")
        data.getAsJsonArray("paths")?.forEach { sj.add(it.asJsonObject.get("file_name").asString) }
        val pathStr = sj.toString()
        val formattedItems = rawZipBeanList.list.map { formatZipBean(it) }
        return rawZipBeanList.copy(pathString = pathStr, list = formattedItems)
    }


    override suspend fun decryptZip(pickCode: String, secret: String): Boolean {
        //{"state":true,"message":"","code":"","data":{"unzip_status":4}}
        val json = fileService.decryptZip(pickCode, secret)
        XLog.v("Post files/push_extract decryptZip $json")
        val asInt = json.data?.unzipStatus ?: -1
        //4 is success,6 is decrypt error,1 is having been done
        return json.state && asInt != 6 && asInt != -1
    }


    override suspend fun checkZipStatus(pickCode: String): ZipStatus {
        return try {
            val response = fileService.getDecryptZipProcess(pickCode)
            //加密压缩包 {"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":6,"progress":88}}}
            //正常无加密压缩包 {"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":4,"progress":100}}}
            //官网显示正在服务器解压 {"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":1,"progress":5}}}
            //官网显示正在服务器解压,意味着加密或者非加密文件{"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":0,"progress":0}}}
            //官网显示正在服务器解压,意味着加密或者非加密文件{"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":2,"progress":30}}}
            XLog.v("Get files/push_extract checkZipStatus $response")
            if (!response.state) {
                ZipStatus.UnsupportedOrError(response.message)
            } else {
                val status = response.data?.extractStatus ?: return ZipStatus.UnsupportedOrError(
                    response.message.ifEmpty { "获取压缩包状态失败" })
                var unzipStatus = status.unzipStatus

                // 2. 特殊状态码二次校验
                if (unzipStatus == 0 || unzipStatus == 2) {
                    val encryptResponse = fileService.checkEncryptionStatus(pickCode)
                    XLog.v("checkEncryptionStatus: $encryptResponse")

                    if (!encryptResponse.state) {
                        return ZipStatus.UnsupportedOrError(encryptResponse.message)
                    }
                    unzipStatus = encryptResponse.data?.unzipStatus ?: -1
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
            XLog.w("checkZipStatus 任务被取消 $e")
            ZipStatus.UnsupportedOrError("任务被取消")
        } catch (e: Exception) {
            // 4. 捕获网络异常、Json语法解析异常等，确保不崩溃
            XLog.e("checkZipStatus 发生异常", e)
            ZipStatus.UnsupportedOrError("网络或系统异常: ${e.localizedMessage}")
        }
    }

    override fun getDownloadUrl(pickCode: String, fileId: String): String? {
        val sha1Util = Sha1Util()
        val okHttpClient = NetworkClient.sharedOkHttpClient
        val tm = System.currentTimeMillis() / 1000
        val m115Encode = sha1Util.m115_encode(pickCode, tm)
        val map = FormBody.Builder().add("data", m115Encode.data).build()

        val request: Request =
            Request.Builder().url("https://proapi.115.com/app/chrome/downurl?t=$tm")
                .addHeader("cookie", UserSessionManager.cookie)
                .addHeader("Content-Type", "application/x-www-form-urlencoded").addHeader(
                    "User-Agent",
                    ConfigKeyUtil.USER_AGENT
                ).post(map).build()

        val response = okHttpClient.newCall(request).execute()

        val returnJson = JsonParser.parseString(response.body.string()).asJsonObject
        XLog.v("FileRepository getDownloadUrl returnJson $returnJson")

        val data = returnJson.get("data").asString
        val m115Decode = sha1Util.m115_decode(data, m115Encode.key)
        XLog.v("FileRepository getDownloadUrl m115Decode $m115Decode")

        //{"fileId":{"file_name":"a","file_size":"0","pick_code":"pick_code","url":false}}
        val downloadUrl = try {
            JsonParser.parseString(m115Decode).asJsonObject.getAsJsonObject(fileId)
                .getAsJsonObject("url").get("url").asString
        } catch (e: Exception) {
            XLog.e("FileRepository getDownloadUrl error ${JsonParser.parseString(m115Decode)}", e)
            null
        }
        XLog.i("FileRepository getDownloadUrl downloadUrl $downloadUrl")
        return downloadUrl;
    }

    override fun getDownloadInputStream(
        pickCode: String, fileId: String
    ): InputStream? {
        val downloadUrl = getDownloadUrl(pickCode, fileId) ?: return null
        val okHttpClient = NetworkClient.sharedOkHttpClient
        val requestDownload: Request =
            Request.Builder().url(downloadUrl).addHeader("cookie", UserSessionManager.cookie)
                .addHeader("Content-Type", "application/x-www-form-urlencoded").addHeader(
                    "User-Agent",
                    ConfigKeyUtil.USER_AGENT
                ).get().build()
        val responseDownload = okHttpClient.newCall(requestDownload).execute();
        val body = responseDownload.body.byteStream()
        return body
    }


    override suspend fun music(pickCode: String): String {
        return fileService.music(pickCode, "weixin", "json").url
    }

    /**
     * 上传本地文件到 115 目录
     * @param file 要上传的本地文件
     * @param targetCid 115 目标目录 CID
     * @param uploadFileName 自定义上传文件名（默认使用 file.name）
     * @param mimeType 文件的 MIME 类型
     */
    override suspend fun uploadFile(
        file: File,
        targetCid: String,
        uploadFileName: String,
        mimeType: String
    ): Base115Response<UploadBean> = withContext(Dispatchers.IO) {
        val uid = UserSessionManager.uid
        val cookie = UserSessionManager.cookie
        val gson = Gson()

        val initUrl = "https://uplb.115.com/3.0/sampleinitupload.php"
        val postBody =
            "userid=$uid&filename=$uploadFileName&filesize=${file.length()}&target=U_1_$targetCid"
                .toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType())

        val initRequest = Request.Builder()
            .url(initUrl)
            .addHeader("cookie", cookie)
            .addHeader("User-Agent", ConfigKeyUtil.USER_AGENT)
            .post(postBody)
            .build()

        val response = NetworkClient.sharedOkHttpClient.newCall(initRequest).execute()
        if (!response.isSuccessful) {
            XLog.e("FileRepository.uploadFile: 初始化上传失败, Code: ${response.code}")
            return@withContext Base115Response(
                state = false,
                message = "初始化上传失败 (HTTP ${response.code})"
            )
        }

        val bodyString = response.body.string()
        val initUploadBean = runCatching {
            gson.fromJson(bodyString, InitUploadBean::class.java)
        }.getOrNull()

        if (initUploadBean == null || initUploadBean.host.isBlank()) {
            XLog.e("FileRepository.uploadFile: 解析初始化上传响应失败: $bodyString")
            return@withContext Base115Response(state = false, message = "解析初始化上传结果失败")
        }

        val requestBody: RequestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("name", uploadFileName)
            .addFormDataPart("key", initUploadBean.key)
            .addFormDataPart("policy", initUploadBean.policy)
            .addFormDataPart("OSSAccessKeyId", initUploadBean.oSSAccessKeyId)
            .addFormDataPart("success_action_status", "200")
            .addFormDataPart("callback", initUploadBean.callback)
            .addFormDataPart("signature", initUploadBean.signature)
            .addFormDataPart(
                "file",
                uploadFileName,
                file.asRequestBody(mimeType.toMediaType())
            )
            .build()

        val uploadRequest = Request.Builder()
            .url(initUploadBean.host)
            .addHeader("origin", "https://115.com")
            .addHeader("referer", "https://115.com")
            .addHeader("cookie", cookie)
            .addHeader("User-Agent", ConfigKeyUtil.USER_AGENT)
            .post(requestBody)
            .build()

        val uploadResponse = NetworkClient.sharedOkHttpClient.newCall(uploadRequest).execute()
        if (!uploadResponse.isSuccessful) {
            XLog.e("FileRepository.uploadFile: 上传文件至 OSS 失败, Code: ${uploadResponse.code}")
            return@withContext Base115Response(
                state = false,
                message = "上传文件失败 (HTTP ${uploadResponse.code})"
            )
        }

        val uploadBody = uploadResponse.body.string()

        val type = object : TypeToken<Base115Response<UploadBean>>() {}.type
        val result = gson.fromJson<Base115Response<UploadBean>>(uploadBody, type)

        runCatching {
            result
        }.getOrElse {
            Base115Response(state = false, message = "解析上传响应失败")
        }
    }
}
