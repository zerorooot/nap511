package github.zerorooot.nap511.repository

import android.content.Context
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.SubtitleItem
import github.zerorooot.nap511.bean.SubtitleSourceType
import github.zerorooot.nap511.bean.XunleiSubtitleResponse
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.SubtitleConverter
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

        private val SUPPORTED_SUBTITLE_EXTS = setOf("srt", "vtt", "ass", "ssa", "sub")
    }

    private val gson by lazy { Gson() }

    /**
     * 结合迅雷 API 与 115 网盘同目录字幕获取完整字幕候选列表，并按时长差值升序排序
     * @param searchKeyword 搜索关健字（默认为去后缀后的文件名）
     * @param parentCid 115 网盘视频所在的父目录 CID
     * @param videoDurationMs 视频播放总时长（毫秒）
     */
    suspend fun getSubtitles(
        searchKeyword: String,
        parentCid: String,
        videoDurationMs: Long
    ): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<SubtitleItem>()

        // 1. 获取迅雷字幕
        val xunleiSubtitles = fetchXunleiSubtitles(searchKeyword)
        resultList.addAll(xunleiSubtitles)

        // 2. 获取 115 网盘同目录下的字幕文件
        if (parentCid.isNotEmpty() && parentCid != "0") {
            val oneOneFiveSubtitles = fetch115SameFolderSubtitles(parentCid)
            resultList.addAll(oneOneFiveSubtitles)
        }

        // 3. 按照字幕时长与视频时长的差值 (abs(subtitleDuration - videoDuration)) 从小到大排序
        if (videoDurationMs > 0) {
            resultList.sortBy { item ->
                if (item.durationMs > 0) {
                    abs(item.durationMs - videoDurationMs)
                } else {
                    Long.MAX_VALUE / 2 // 无时长信息的字幕排在后侧
                }
            }
        }

        XLog.i("SubtitleRepository: 搜索关键字 [$searchKeyword] 共获取 ${resultList.size} 条字幕 (迅雷: ${xunleiSubtitles.size}, 115: ${resultList.size - xunleiSubtitles.size})")
        resultList
    }

    /**
     * 请求迅雷字幕 API: http://api-shoulei-ssl.xunlei.com/oracle/subtitle?name={searchKeyword}
     */
    suspend fun fetchXunleiSubtitles(searchKeyword: String): List<SubtitleItem> = withContext(Dispatchers.IO) {
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

            val bodyString = response.body?.string() ?: return@withContext emptyList()
            val parsedResponse = gson.fromJson(bodyString, XunleiSubtitleResponse::class.java)

            if (parsedResponse.code == 0 && !parsedResponse.data.isNullOrEmpty()) {
                parsedResponse.data.mapIndexed { index, item ->
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
     * 读取 115 网盘同目录 CID 下支持的字幕文件
     */
    private suspend fun fetch115SameFolderSubtitles(parentCid: String): List<SubtitleItem> = withContext(Dispatchers.IO) {
        try {
            val filesBean = fileRepository.getFiles(cid = parentCid, order = "file_name", asc = 1)
            val fileList = filesBean.fileBeanList

            fileList.filter { fileBean ->
                !fileBean.isFolder && isSubtitleFile(fileBean.name)
            }.map { fileBean ->
                val ext = fileBean.name.substringAfterLast('.', "srt").lowercase(Locale.US)
                val durationMs = (fileBean.playLong * 1000).toLong()

                SubtitleItem(
                    id = "115_${fileBean.pickCode}",
                    name = fileBean.name,
                    simpleName = fileBean.name,
                    sourceType = SubtitleSourceType.ONE_ONE_FIVE,
                    pickCode = fileBean.pickCode,
                    fileId = fileBean.fileId,
                    ext = ext,
                    durationMs = durationMs
                )
            }
        } catch (e: Exception) {
            XLog.e("SubtitleRepository: 获取 115 网盘同目录字幕失败", e)
            emptyList()
        }
    }

    /**
     * 判断文件名是否为支持的字幕后缀
     */
    private fun isSubtitleFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return SUPPORTED_SUBTITLE_EXTS.contains(ext)
    }

    /**
     * 下载字幕并处理转换（若为 ASS/SSA/SUB 格式自动转换为 SRT 文本）
     * @return 转换或保存后的标准本地 SRT/VTT File，方便传给播放器 GSYSubtitleSource
     */
    suspend fun downloadAndPrepareSubtitle(context: Context, item: SubtitleItem): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "subtitles").apply { if (!exists()) mkdirs() }
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

            val response = NetworkClient.sharedOkHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                XLog.e("SubtitleRepository: 下载字幕文件失败, Code: ${response.code}")
                return@withContext null
            }

            val bytes = response.body.bytes()
            rawFile.writeBytes(bytes)

            // 3. 执行格式判断与转码
            val finalFile = SubtitleConverter.convertToSrtIfNeeded(rawFile, targetSrtFile, item.ext)
            finalFile
        } catch (e: Exception) {
            XLog.e("SubtitleRepository: 下载准备字幕异常: ${item.name}", e)
            null
        }
    }
}
