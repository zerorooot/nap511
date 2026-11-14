package github.zerorooot.nap511.data.repository

import com.google.gson.JsonObject
import github.zerorooot.nap511.data.model.Base115Response
import github.zerorooot.nap511.data.model.BaseReturnMessage
import github.zerorooot.nap511.data.model.CreateFolderMessage
import github.zerorooot.nap511.data.model.FileBean
import github.zerorooot.nap511.data.model.FileInfo
import github.zerorooot.nap511.data.model.FilesBean
import github.zerorooot.nap511.data.model.ImageDate
import github.zerorooot.nap511.data.model.OfflineInfo
import github.zerorooot.nap511.data.model.OfflineListCount
import github.zerorooot.nap511.data.model.OfflineTaskType
import github.zerorooot.nap511.data.model.OrderBean
import github.zerorooot.nap511.data.model.QuotaBean
import github.zerorooot.nap511.data.model.RecycleInfo
import github.zerorooot.nap511.data.model.SignBean
import github.zerorooot.nap511.data.model.TorrentFileBean
import github.zerorooot.nap511.data.model.UploadBean
import github.zerorooot.nap511.data.model.VideoInfoBean
import github.zerorooot.nap511.data.model.ZipBeanList
import github.zerorooot.nap511.data.model.ZipStatus
import github.zerorooot.nap511.data.network.client.UserSessionManager
import okhttp3.RequestBody
import java.io.File
import java.io.InputStream

/**
 * 文件与网盘数据仓库接口
 * 统管网盘文件、目录、离线下载、压缩包及媒体历史等数据交互。
 */
interface FileRepository {

    //-----------------离线下载相关--------------------------------
    suspend fun getOfflineSign(): SignBean

    suspend fun getOfflineTaskCount(): OfflineListCount

    suspend fun getOfflineTaskList(
        uid: String, sign: String, page: Int, offlineTaskType: OfflineTaskType
    ): OfflineInfo

    suspend fun getOfflineTorrentTaskList(
        sha1: String = "",
        sign: String = "",
        uid: String = "",
    ): TorrentFileBean

    suspend fun addOfflineTorrentTask(
        infoHash: String = "",
        wanted: String = "",
        savePath: String = "",
        uid: String = "",
        sign: String,
    ): BaseReturnMessage

    suspend fun quota(): QuotaBean

    suspend fun addOfflineTask(
        list: List<String>, currentCid: String, offlinePath: String, handle: (Boolean) -> Unit
    ): Pair<Boolean, String>

    suspend fun deleteOfflineTask(body: HashMap<String, String>): BaseReturnMessage

    suspend fun clearOfflineError(): BaseReturnMessage

    suspend fun clearOfflineFinish(): BaseReturnMessage

    //-----------------离线下载结束--------------------------------

    suspend fun getFiles(
        cid: String,
        showDir: Int = 1,
        aid: Int = 1,
        asc: Int = 1,
        naturalSort: Int = 1,
        order: String = "file_name",
        limit: Int = UserSessionManager.requestLimitCount,
        format: String = "json",
        offset: Int = 0,
        fcMix: Int,
    ): FilesBean

    suspend fun remainingSpace(countSpaceNum: Int = 1): JsonObject

    suspend fun order(body: Map<String, String>): BaseReturnMessage

    suspend fun deleteMultiple(data: Map<String, String>): BaseReturnMessage

    suspend fun search(
        cid: String,
        searchValue: String,
        aid: Int = 1,
        offset: Int = 0,
        limit: Int = UserSessionManager.requestLimitCount
    ): FilesBean

    suspend fun filterFile(
        cid: String,
        type: Int,
        order: String,
        asc: Int = 1,
        offset: Int = 0,
        limit: Int = UserSessionManager.requestLimitCount,
        fcMix: Int = 0
    ): FilesBean

    suspend fun starFile(
        offset: Int,
        order: OrderBean,
        fcMix: Int,
        limit: Int,
    ): FilesBean

    /**
     * 文件加⭐，true 表示加上，false 表示取消
     */
    suspend fun star(fid: String, star: Boolean): BaseReturnMessage

    /**
     * 文件加⭐，true 表示加上，false 表示取消
     */
    suspend fun top(fid: String, top: Boolean): BaseReturnMessage

    suspend fun image(pickCode: String, current: Long): ImageDate

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
    suspend fun updateVideoHistory(
        pickCode: String,
        currentDuration: Int,
        name: String = ""
    ): Boolean

    suspend fun video(pickCode: String): VideoInfoBean

    suspend fun getFileInfo(cid: String): FileInfo

    suspend fun createFolder(
        pid: String, folderName: String
    ): CreateFolderMessage

    /**
     *  @param currentCid 要移动到的目录
     *  @param removeAllFile 要移动的文件
     */
    suspend fun removeAllFile(
        currentCid: String,
        removeAllFile: List<FileBean>
    ): BaseReturnMessage

    suspend fun setDownloadPath(cid: String): BaseReturnMessage

    suspend fun move(body: Map<String, String>): BaseReturnMessage

    suspend fun recycleList(
        aid: String = "7",
        cid: String = "0",
        offset: String = "0",
        limit: String = "999"
    ): RecycleInfo

    suspend fun revert(rid: String): BaseReturnMessage

    suspend fun recycleCleanAll(password: String = ""): BaseReturnMessage

    /**
     * 根据文件名，创建一个去除后缀的文件夹，如果文件夹存在则返回当前cid,不存在则返回文件夹cid
     */
    suspend fun createFolderAndReturnCid(cid: String, fileName: String): String

    suspend fun delete(pid: String, fid: String): BaseReturnMessage

    suspend fun rename(renameBean: RequestBody): BaseReturnMessage

    suspend fun unzipFile(
        pickCode: String,
        zipFileCid: String,
        files: List<String>?,
        dirs: List<String>?,
        unzipFolderName: String = ""
    ): Pair<Boolean, String>

    /**
     * {"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":4,"progress":100}}}
     */
    suspend fun tryToExtract(pickCode: String): Boolean

    suspend fun getZipListFile(
        pickCode: String,
        fileName: String = "",
        paths: String = "文件"
    ): ZipBeanList

    suspend fun decryptZip(pickCode: String, secret: String): Boolean

    suspend fun checkZipStatus(pickCode: String): ZipStatus

    fun getDownloadUrl(pickCode: String, fileId: String): String?

    fun getDownloadInputStream(
        pickCode: String, fileId: String
    ): InputStream?

    suspend fun music(pickCode: String): String

    /**
     * 上传本地文件到 115 目录
     * @param file 要上传的本地文件
     * @param targetCid 115 目标目录 CID
     * @param uploadFileName 自定义上传文件名（默认使用 file.name）
     * @param mimeType 文件的 MIME 类型
     */
    suspend fun uploadFile(
        file: File,
        targetCid: String,
        uploadFileName: String = file.name,
        mimeType: String = "application/octet-stream"
    ): Base115Response<UploadBean>
}