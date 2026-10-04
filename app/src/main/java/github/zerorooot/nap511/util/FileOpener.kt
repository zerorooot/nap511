package github.zerorooot.nap511.util

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import com.google.gson.Gson
import github.zerorooot.nap511.R
import github.zerorooot.nap511.activity.VideoActivity
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.LaunchVideoParams
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.bean.SubtitleItem
import github.zerorooot.nap511.bean.SubtitleSourceType
import github.zerorooot.nap511.bean.VideoAttributeBean
import github.zerorooot.nap511.bean.VideoBean
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 文件打开结果状态
 */
sealed interface FileOpenResult {
    data class Success(val mediaType: String, val message: String) : FileOpenResult
    data class Failure(val message: String) : FileOpenResult
    data class Unsupported(val fileName: String) : FileOpenResult
}

/**
 * 伴随字幕辅助方法
 */
fun isSubtitleFile(fileName: String): Boolean {
    val supportedSubtitleExts = setOf("srt", "vtt", "ass", "ssa", "sub", "lrc")
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
    return supportedSubtitleExts.contains(ext)
}

fun extractSubtitles(files: List<FileBean>): List<SubtitleItem> {
    return files.filter { !it.isFolder && isSubtitleFile(it.name) }.map { fileBean ->
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
}

/**
 * 公共文件打开器（纯领域类，无 ViewModel 依赖）
 *
 * 遵循高内聚、低耦合原则：
 * 1. 查看器瞬态数据与缓存依赖 [MediaViewerStateHolder]；
 * 2. 音频控制依赖 [AudioPlayerController] 抽象接口；
 * 3. 弹窗交互依赖 [FileDialogController] 抽象接口；
 * 4. 统一处理各类文件（视频、音频、图片、文本、网页、压缩包、种子）的校验、下载、参数装配与路由导航。
 */
class FileOpener(
    private val context: Context,
    private val fileRepository: FileRepository = FileRepository.getInstance(),
    private val mediaViewerStateHolder: MediaViewerStateHolder,
    private val audioPlayerController: AudioPlayerController,
    private val fileDialogController: FileDialogController? = null,
    private val settingUiState: () -> SettingUiState,
    private val onNavigate: (Route) -> Unit
) {

    /**
     * 统一打开入口
     *
     * @param fileBean 要打开的文件对象
     * @param siblingFiles 同级目录下的文件列表（用于装配连播视频列表、图片轮播列表或匹配伴随字幕）
     * @param fromTerminal 是否来自命令行终端环境（为 true 时打开音频会自动跳转音乐详情）
     */
    suspend fun open(
        fileBean: FileBean,
        siblingFiles: List<FileBean> = emptyList(),
        fromTerminal: Boolean = false
    ): FileOpenResult {
        return when {
            fileBean.isVideo == 1 -> {
                val ok = openVideo(fileBean, siblingFiles)
                if (ok) FileOpenResult.Success("video", "已启动视频播放器: ${fileBean.name}")
                else FileOpenResult.Failure("获取视频播放信息失败")
            }
            fileBean.photoThumb.isNotEmpty() || fileBean.fileIco == R.drawable.png -> {
                openPhoto(fileBean, siblingFiles)
                FileOpenResult.Success("photo", "已打开图片预览: ${fileBean.name}")
            }
            fileBean.fileIco == R.drawable.mp3 -> {
                openAudio(fileBean, siblingFiles, navigateToDetail = fromTerminal)
                FileOpenResult.Success("audio", "已开始播放音频: ${fileBean.name}")
            }
            fileBean.fileIco == R.drawable.txt -> {
                val ok = openText(fileBean)
                if (ok) FileOpenResult.Success("text", "已打开文本阅读器: ${fileBean.name}")
                else FileOpenResult.Failure("文本打开失败或超出大小限制")
            }
            fileBean.fileIco == R.drawable.web -> {
                val ok = openWeb(fileBean)
                if (ok) FileOpenResult.Success("web", "已打开网页预览: ${fileBean.name}")
                else FileOpenResult.Failure("网页打开失败或超出大小限制")
            }
            fileBean.fileIco == R.drawable.torrent -> {
                val ok = openTorrent(fileBean)
                if (ok) FileOpenResult.Success("torrent", "已提交种子解析任务: ${fileBean.name}")
                else FileOpenResult.Failure("当前环境不支持打开种子解析弹窗")
            }
            fileBean.fileIco == R.drawable.zip -> {
                val ok = openZip(fileBean)
                if (ok) FileOpenResult.Success("zip", "已打开压缩包预览: ${fileBean.name}")
                else FileOpenResult.Failure("当前环境不支持打开压缩包弹窗，请使用 'unzip -l' 预览")
            }
            else -> {
                FileOpenResult.Unsupported(fileBean.name)
            }
        }
    }

    /**
     * 打开视频文件并启动 [VideoActivity]
     */
    suspend fun openVideo(fileBean: FileBean, siblingFiles: List<FileBean> = emptyList()): Boolean {
        audioPlayerController.pauseAudio()
        val videoList = siblingFiles.filter { it.isVideo == 1 && it.playLong != 0.0 }.map {
            VideoBean(
                name = it.name,
                pickCode = it.pickCode,
                fileId = it.fileId,
                time = it.playLongString
            )
        }.ifEmpty {
            listOf(
                VideoBean(
                    name = fileBean.name,
                    pickCode = fileBean.pickCode,
                    fileId = fileBean.fileId,
                    time = fileBean.playLongString
                )
            )
        }
        val fileBeanIndex = videoList.indexOfFirst { it.pickCode == fileBean.pickCode }.coerceAtLeast(0)
        val localSubtitleList = extractSubtitles(siblingFiles)

        val settings = settingUiState()
        val video = withContext(Dispatchers.IO) {
            try {
                if (settings.videoLinkMode) {
                    fileRepository.video(fileBean.pickCode).copy(index = fileBeanIndex)
                } else {
                    val (width, height) = if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                        1080 to 1920
                    } else {
                        1920 to 1080
                    }
                    VideoInfoBean(
                        width = width,
                        height = height,
                        index = fileBeanIndex,
                        fileName = fileBean.name,
                        pickCode = fileBean.pickCode,
                        videoUrl = "http://115.com/api/video/m3u8/${fileBean.pickCode}.m3u8"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        if (video == null) {
            App.instance.toast("获取视频播放信息失败")
            return false
        }

        val videoAttributeBean = VideoAttributeBean(
            isAutoRotate = settings.autoRotateEnabled,
            videoLinkMode = settings.videoLinkMode,
            autoJumpRetry = settings.autoJumpRetry,
            hideLoading = settings.hideLoadingView,
            positionAfterAt = settings.positionAfterAt
        )
        val launchVideoParams = LaunchVideoParams(
            videoInfo = video,
            videoAttribute = videoAttributeBean,
            localSubtitleItem = localSubtitleList,
            videoList = videoList,
            categoryId = fileBean.categoryId
        )
        val launchVideoParamsJson = Gson().toJson(launchVideoParams, LaunchVideoParams::class.java)
        val intent = Intent(context, VideoActivity::class.java).apply {
            putExtra("bean", launchVideoParamsJson)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    /**
     * 打开图片并导航至图片全屏浏览器
     */
    fun openPhoto(fileBean: FileBean, photoList: List<FileBean> = emptyList()): Boolean {
        audioPlayerController.pauseAudio()
        val validPhotoList = photoList.filter { it.photoThumb.isNotEmpty() || it.fileIco == R.drawable.png }
            .ifEmpty { listOf(fileBean) }

        val targetIndex = validPhotoList.indexOfFirst { it.pickCode == fileBean.pickCode || it.fileId == fileBean.fileId }
        val finalIndex = if (targetIndex >= 0) targetIndex else 0
        val targetCid = fileBean.categoryId.ifEmpty { fileBean.parentId }

        mediaViewerStateHolder.setPhotoList(list = validPhotoList, index = finalIndex, cid = targetCid)
        onNavigate(Route.Photo)
        return true
    }

    /**
     * 播放音频并关联字幕，支持可选跳转至 [Route.MusicDetail]
     */
    fun openAudio(
        fileBean: FileBean,
        siblingFiles: List<FileBean> = emptyList(),
        navigateToDetail: Boolean = false
    ): Boolean {
        val localSubtitles = extractSubtitles(siblingFiles)
        audioPlayerController.playAudio(fileBean, localSubtitles)
        if (navigateToDetail) {
            onNavigate(Route.MusicDetail)
        }
        return true
    }

    /**
     * 校验大小、拉取/读取缓存字节流并导航至文本阅读器
     */
    suspend fun openText(fileBean: FileBean): Boolean {
        val settings = settingUiState()
        val txtSize = settings.txtSize.toIntOrNull() ?: 200
        if (fileBean.size.toLong() >= txtSize * 1024) {
            App.instance.toast("仅支持打开${txtSize}kb以下的文件")
            return false
        }

        val bytes = withContext(Dispatchers.IO) {
            var cached = mediaViewerStateHolder.getCachedBytes(fileBean)
            if (cached == null) {
                runCatching {
                    val inputStream = fileRepository.getDownloadInputStream(fileBean.pickCode, fileBean.fileId)
                    if (inputStream != null) {
                        cached = inputStream.readBytes()
                        mediaViewerStateHolder.putCachedBytes(fileBean, cached)
                    }
                }
            }
            cached
        }

        if (bytes != null) {
            withContext(Dispatchers.Main) {
                mediaViewerStateHolder.setTextContent(bytes)
                onNavigate(Route.TxtReader(title = fileBean.name))
            }
            return true
        } else {
            App.instance.toast("文件加载失败！")
            return false
        }
    }

    /**
     * 校验大小、拉取/读取缓存字节流并导航至网页预览器
     */
    suspend fun openWeb(fileBean: FileBean): Boolean {
        val settings = settingUiState()
        val txtSize = settings.txtSize.toIntOrNull() ?: 200
        if (fileBean.size.toLong() >= txtSize * 1024) {
            App.instance.toast("仅支持打开${txtSize}kb以下的文件")
            return false
        }

        val bytes = withContext(Dispatchers.IO) {
            var cached = mediaViewerStateHolder.getCachedBytes(fileBean)
            if (cached == null) {
                runCatching {
                    val inputStream = fileRepository.getDownloadInputStream(fileBean.pickCode, fileBean.fileId)
                    if (inputStream != null) {
                        cached = inputStream.readBytes()
                        mediaViewerStateHolder.putCachedBytes(fileBean, cached)
                    }
                }
            }
            cached
        }

        if (bytes != null) {
            withContext(Dispatchers.Main) {
                mediaViewerStateHolder.setWebContent(bytes)
                onNavigate(Route.HtmlWebViewScreen(title = fileBean.name))
            }
            return true
        } else {
            App.instance.toast("文件加载失败！")
            return false
        }
    }

    /**
     * 提交/打开种子任务弹窗
     */
    fun openTorrent(fileBean: FileBean): Boolean {
        return fileDialogController?.let {
            it.openTorrent(fileBean)
            true
        } ?: false
    }

    /**
     * 打开压缩包预览弹窗
     */
    fun openZip(fileBean: FileBean): Boolean {
        return fileDialogController?.let {
            it.openZip(fileBean)
            true
        } ?: false
    }
}
