package github.zerorooot.nap511.repository

import com.elvishew.xlog.XLog
import com.google.gson.Gson
import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.SubtitleItem
import github.zerorooot.nap511.bean.SubtitleSourceType
import github.zerorooot.nap511.bean.XunleiSubtitleResponse
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.subtitle.SubtitleConverter
import github.zerorooot.nap511.util.network.NetworkClient
import github.zerorooot.nap511.util.network.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

/**
 * 字幕 Repository
 * 负责从迅雷 API 与 115 网盘同目录获取字幕列表、进行距离排序以及处理字幕文件下载与格式转换。
 */
class SubtitleRepository(
    private val fileRepository: FileRepository = FileRepository.getInstance()
) {
    companion object {
        @Volatile
        private var INSTANCE: SubtitleRepository? = null

        fun getInstance(): SubtitleRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SubtitleRepository().also { INSTANCE = it }
            }
        }
    }

    private val gson by lazy { Gson() }

    /**
     * 结合迅雷 API 与 115 网盘同目录字幕获取完整字幕候选列表，并按时长差值升序排序
     * @param searchKeyword 搜索关健字（默认为去后缀后的文件名）
     * @param oneOneFiveSubtitles 115 网盘视频下的字幕文件
     * @param videoDurationMs 视频播放总时长（毫秒）
     */
    suspend fun getSubtitles(
        searchKeyword: String,
        oneOneFiveSubtitles: List<SubtitleItem>,
        videoDurationMs: Long
    ): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<SubtitleItem>()

        // 1. 获取迅雷字幕
        val xunleiSubtitles = fetchXunleiSubtitles(searchKeyword)

        // 2. 获取 115 网盘同目录下的字幕文件
        if (oneOneFiveSubtitles.isNotEmpty()) {
            resultList.addAll(oneOneFiveSubtitles)
        }

        // 3. 按照字幕时长与视频时长的差值 (abs(subtitleDuration - videoDuration)) 从小到大排序
        if (videoDurationMs > 0) {
            resultList.addAll(xunleiSubtitles.sortedBy { item ->
                if (item.durationMs > 0) {
                    abs(item.durationMs - videoDurationMs)
                } else {
                    Long.MAX_VALUE / 2 // 无时长信息的字幕排在后侧
                }
            })
        }

        XLog.i("SubtitleRepository: 搜索关键字 [$searchKeyword] 共获取 ${resultList.size} 条字幕 (迅雷: ${xunleiSubtitles.size}, 115: ${resultList.size - xunleiSubtitles.size})")
        resultList
    }

    /**
     * 请求迅雷字幕 API: http://api-shoulei-ssl.xunlei.com/oracle/subtitle?name={searchKeyword}
     */
    suspend fun fetchXunleiSubtitles(searchKeyword: String): List<SubtitleItem> =
        withContext(Dispatchers.IO) {
            if (searchKeyword.isBlank()) return@withContext emptyList()

            try {
                val encodedName = URLEncoder.encode(searchKeyword, "UTF-8")
                val url = "http://api-shoulei-ssl.xunlei.com/oracle/subtitle?name=$encodedName"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", ConfigKeyUtil.USER_AGENT)
                    .build()

                val response = NetworkClient.sharedOkHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    XLog.e("SubtitleRepository: 请求迅雷字幕接口失败, Code: ${response.code}")
                    return@withContext emptyList()
                }

                val bodyString = response.body.string()
                val parsedResponse = gson.fromJson(bodyString, XunleiSubtitleResponse::class.java)

                if (parsedResponse.code == 0 && !parsedResponse.data.isNullOrEmpty()) {
                    parsedResponse.data.filter { it.duration > 0 }.mapIndexed { index, item ->
                        val ext = item.ext?.lowercase(Locale.US) ?: "srt"
                        val id = "xunlei_${item.gcid ?: item.cid ?: index}"
                        val name = item.name ?: "$searchKeyword.$ext"
                        val simpleName = item.simpleName?.ifEmpty { null } ?: name

                        SubtitleItem(
                            id = id,
                            name = name,
                            simpleName = simpleName,
                            sourceType = SubtitleSourceType.XUNLEI,
                            url = item.url ?: "",
                            ext = ext,
                            durationMs = item.duration,
                            languages = item.languages ?: emptyList()
                        )
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                XLog.e("SubtitleRepository: 异常获取迅雷字幕", e)
                emptyList()
            }
        }

    /**
     * 下载字幕并处理转换（若为 ASS/SSA/SUB 格式自动转换为 SRT 文本）
     * @return 转换或保存后的标准本地 SRT/VTT File，方便传给播放器 GSYSubtitleSource
     */
    suspend fun downloadAndPrepareSubtitle(cacheDirFile: File, item: SubtitleItem): File? =
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = File(cacheDirFile, "subtitles").apply { if (!exists()) mkdirs() }
                val rawFile = File(cacheDir, "raw_${item.id}.${item.ext}")
                val targetSrtFile = File(cacheDir, "ready_${item.id}.srt")

                // 如果最终已准备好的 srt 文件存在且非空，直接返回缓存
                if (targetSrtFile.exists() && targetSrtFile.length() > 0) {
                    return@withContext targetSrtFile
                }

                // 1. 获取真实下载 URL
                val downloadUrl = when (item.sourceType) {
                    SubtitleSourceType.XUNLEI -> item.url
                    SubtitleSourceType.ONE_ONE_FIVE -> {
                        fileRepository.getDownloadUrl(item.pickCode, item.fileId) ?: ""
                    }
                }

                if (downloadUrl.isBlank()) {
                    XLog.e("SubtitleRepository: 获取字幕下载链接为空: ${item.name}")
                    return@withContext null
                }

                // 2. 执行网络下载
                val requestBuilder = Request.Builder().url(downloadUrl)
                if (item.sourceType == SubtitleSourceType.ONE_ONE_FIVE) {
                    requestBuilder.addHeader("Cookie", UserSessionManager.cookie)
                    requestBuilder.addHeader("User-Agent", ConfigKeyUtil.USER_AGENT)
                }

                val response =
                    NetworkClient.sharedOkHttpClient.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    XLog.e("SubtitleRepository: 下载字幕文件失败, Code: ${response.code}")
                    return@withContext null
                }

                val bytes = response.body.bytes()
                rawFile.writeBytes(bytes)

                // 3. 执行格式判断与转码
                val finalFile =
                    SubtitleConverter.convertToSrtIfNeeded(rawFile, targetSrtFile, item.ext)
                finalFile
            } catch (e: Exception) {
                XLog.e("SubtitleRepository: 下载准备字幕异常: ${item.name}", e)
                null
            }
        }

    /**
     * 将字幕文件下载并上传到 115 目录
     * @param cacheDirFile context.cacheDir
     * @param item 字幕数据对象
     * @param targetCid 115 目标目录 CID
     */
    suspend fun uploadSubtitleTo115(
        cacheDirFile: File,
        item: SubtitleItem,
        targetCid: String
    ): BaseReturnMessage = withContext(Dispatchers.IO) {
        if (targetCid.isBlank() || targetCid == "0") {
            return@withContext BaseReturnMessage(
                state = false,
                message = "当前视频未获取到有效父目录 CID"
            )
        }

        // 1. 下载并转换/准备本地字幕文件
        val srtFile = downloadAndPrepareSubtitle(cacheDirFile, item)
            ?: return@withContext BaseReturnMessage(state = false, message = "下载字幕文件失败")

        // 2. 构造目标文件名（保证扩展名为 .srt 或原始扩展名）
        val rawName = item.name.ifBlank { item.simpleName }.ifBlank { "subtitle" }
        val uploadFileName = if (rawName.endsWith(".srt", ignoreCase = true)) {
            rawName
        } else if (rawName.contains(".")) {
            rawName.substringBeforeLast(".") + ".srt"
        } else {
            "$rawName.srt"
        }

        val cacheDir = srtFile.parentFile ?: cacheDirFile
        val tempUploadFile = File(cacheDir, uploadFileName)
        srtFile.copyTo(tempUploadFile, overwrite = true)

        // 3. 调用 FileRepository 上传接口
        val result = fileRepository.uploadFile(
            file = tempUploadFile,
            targetCid = targetCid,
            uploadFileName = uploadFileName,
            mimeType = "application/x-subrip"
        )

        // 4. 删除用于重命名的临时文件
        if (tempUploadFile.exists() && tempUploadFile != srtFile) {
            tempUploadFile.delete()
        }


        result
    }
}
