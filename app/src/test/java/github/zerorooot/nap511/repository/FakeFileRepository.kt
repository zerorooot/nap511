package github.zerorooot.nap511.repository

import com.google.gson.JsonObject
import github.zerorooot.nap511.bean.Base115Response
import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.CreateFolderMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileInfo
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.ImageDate
import github.zerorooot.nap511.bean.OfflineInfo
import github.zerorooot.nap511.bean.OfflineListCount
import github.zerorooot.nap511.bean.OfflineTaskType
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.QuotaBean
import github.zerorooot.nap511.bean.RecycleInfo
import github.zerorooot.nap511.bean.SignBean
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.bean.UploadBean
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.bean.ZipStatus
import okhttp3.RequestBody
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * 单元测试专用的 [FileRepository] 安全空桩
 * 为全部接口方法提供无副作用的安全默认返回值，方便各类单测按需仅仅 override 感兴趣的目标方法。
 */
open class FakeFileRepository : FileRepository {
    override suspend fun getOfflineSign(): SignBean = SignBean(state = true, sign = "mock_sign")

    override suspend fun getOfflineTaskCount(): OfflineListCount = OfflineListCount(state = true)

    override suspend fun getOfflineTaskList(
        uid: String, sign: String, page: Int, offlineTaskType: OfflineTaskType
    ): OfflineInfo = OfflineInfo(state = true)

    override suspend fun getOfflineTorrentTaskList(
        sha1: String, sign: String, uid: String
    ): TorrentFileBean = TorrentFileBean(state = true)

    override suspend fun addOfflineTorrentTask(
        infoHash: String, wanted: String, savePath: String, uid: String, sign: String
    ): BaseReturnMessage = BaseReturnMessage(state = true)

    override suspend fun quota(): QuotaBean = QuotaBean(count = 0, surplus = 0)

    override suspend fun addOfflineTask(
        list: List<String>, currentCid: String, offlinePath: String, handle: (Boolean) -> Unit
    ): Pair<Boolean, String> = Pair(true, "mock_task_id")

    override suspend fun deleteOfflineTask(body: HashMap<String, String>): BaseReturnMessage =
        BaseReturnMessage(state = true)

    override suspend fun clearOfflineError(): BaseReturnMessage = BaseReturnMessage(state = true)

    override suspend fun clearOfflineFinish(): BaseReturnMessage = BaseReturnMessage(state = true)

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
        fcMix: Int
    ): FilesBean = FilesBean(fileBeanList = arrayListOf())

    override suspend fun remainingSpace(countSpaceNum: Int): JsonObject = JsonObject()

    override suspend fun order(body: Map<String, String>): BaseReturnMessage =
        BaseReturnMessage(state = true)

    override suspend fun deleteMultiple(data: Map<String, String>): BaseReturnMessage =
        BaseReturnMessage(state = true)

    override suspend fun search(
        cid: String, searchValue: String, aid: Int, offset: Int, limit: Int
    ): FilesBean = FilesBean(fileBeanList = arrayListOf())

    override suspend fun filterFile(
        cid: String, type: Int, order: String, asc: Int, offset: Int, limit: Int, fcMix: Int
    ): FilesBean = FilesBean(fileBeanList = arrayListOf())

    override suspend fun starFile(
        offset: Int, order: OrderBean, fcMix: Int, limit: Int
    ): FilesBean = FilesBean(fileBeanList = arrayListOf())

    override suspend fun star(fid: String, star: Boolean): BaseReturnMessage =
        BaseReturnMessage(state = true)

    override suspend fun top(fid: String, top: Boolean): BaseReturnMessage =
        BaseReturnMessage(state = true)

    override suspend fun image(pickCode: String, current: Long): ImageDate = ImageDate(state = true)

    override suspend fun updateVideoHistory(
        pickCode: String, currentDuration: Int, name: String
    ): Boolean = true

    override suspend fun video(pickCode: String): VideoInfoBean = VideoInfoBean()

    override suspend fun getFileInfo(cid: String): FileInfo = FileInfo()

    override suspend fun createFolder(
        pid: String, folderName: String
    ): CreateFolderMessage = CreateFolderMessage(state = true)

    override suspend fun removeAllFile(
        currentCid: String, removeAllFile: List<FileBean>
    ): BaseReturnMessage = BaseReturnMessage(state = true)

    override suspend fun setDownloadPath(cid: String): BaseReturnMessage =
        BaseReturnMessage(state = true)

    override suspend fun move(body: Map<String, String>): BaseReturnMessage =
        BaseReturnMessage(state = true)

    override suspend fun recycleList(
        aid: String, cid: String, offset: String, limit: String
    ): RecycleInfo = RecycleInfo(state = true)

    override suspend fun revert(rid: String): BaseReturnMessage = BaseReturnMessage(state = true)

    override suspend fun recycleCleanAll(password: String): BaseReturnMessage =
        BaseReturnMessage(state = true)

    override suspend fun createFolderAndReturnCid(cid: String, fileName: String): String = cid

    override suspend fun delete(pid: String, fid: String): BaseReturnMessage =
        BaseReturnMessage(state = true)

    override suspend fun rename(renameBean: RequestBody): BaseReturnMessage =
        BaseReturnMessage(state = true)

    override suspend fun unzipFile(
        pickCode: String,
        zipFileCid: String,
        files: List<String>?,
        dirs: List<String>?,
        unzipFolderName: String
    ): Pair<Boolean, String> = Pair(true, "解压成功")

    override suspend fun tryToExtract(pickCode: String): Boolean = true

    override suspend fun getZipListFile(
        pickCode: String, fileName: String, paths: String
    ): ZipBeanList = ZipBeanList()

    override suspend fun decryptZip(pickCode: String, secret: String): Boolean = true

    override suspend fun checkZipStatus(pickCode: String): ZipStatus = ZipStatus.Normal

    override fun getDownloadUrl(pickCode: String, fileId: String): String? =
        "https://mock.download.url"

    override fun getDownloadInputStream(pickCode: String, fileId: String): InputStream? =
        ByteArrayInputStream(byteArrayOf())

    override suspend fun music(pickCode: String): String = "https://mock.music.url"

    override suspend fun uploadFile(
        file: File, targetCid: String, uploadFileName: String, mimeType: String
    ): Base115Response<UploadBean> = Base115Response(state = true)
}
