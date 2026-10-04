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
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.getTorrentTask
import github.zerorooot.nap511.viewmodel.getZipListFile
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
 * 公共文件打开器
 * 统一处理各类文件（视频、音频、图片、文本、网页、压缩包、种子）的校验、下载、参数装配与界面路由导航
 */
class FileOpener(
    private val context: Context,
    private val fileRepository: FileRepository = FileRepository.getInstance(),
    private val fileViewModel: FileViewModel,
    private val audioViewModel: AudioViewModel,
    private val settingUiState: () -> SettingUiState,
    private val onNavigate: (Route) -> Unit
) {

    /**
     * 统一打开入口
     */
    suspend fun open(
        fileBean: FileBean,
        siblingFiles: List<FileBean> = emptyList(),
        fromTerminal: Boolean = false
    ): FileOpenResult {
        return when {
            fileBean.isVideo == 1 -> {
                openVideo(fileBean, siblingFiles)
                FileOpenResult.Success("video", "已启动视频播放器: ${fileBean.name}")
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
                openTorrent(fileBean)
                FileOpenResult.Success("torrent", "已提交种子解析任务: ${fileBean.name}")
            }
            fileBean.fileIco == R.drawable.zip -> {
                openZip(fileBean)
                FileOpenResult.Success("zip", "已打开压缩包预览: ${fileBean.name}")
            }
            else -> {
                FileOpenResult.Unsupported(fileBean.name)
            }
        }
    }

    /**
     * 打开视频
     */
    suspend fun openVideo(fileBean: FileBean, siblingFiles: List<FileBean> = emptyList()): Boolean {
        audioViewModel.pause()
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
     * 打开图片
     */
    fun openPhoto(fileBean: FileBean, photoList: List<FileBean> = emptyList()): Boolean {
        audioViewModel.pause()
        val validPhotoList = photoList.filter { it.photoThumb.isNotEmpty() || it.fileIco == R.drawable.png }
            .ifEmpty { listOf(fileBean) }

        fileViewModel.photoFileBeanList.clear()
        fileViewModel.photoFileBeanList.addAll(validPhotoList)
        val targetIndex = validPhotoList.indexOfFirst { it.pickCode == fileBean.pickCode || it.fileId == fileBean.fileId }
        fileViewModel.photoIndexOf = if (targetIndex >= 0) targetIndex else 0
        fileViewModel.currentCid = fileBean.categoryId.ifEmpty { fileBean.parentId }
        onNavigate(Route.Photo)
        return true
    }

    /**
     * 打开音频
     */
    fun openAudio(
        fileBean: FileBean,
        siblingFiles: List<FileBean> = emptyList(),
        navigateToDetail: Boolean = false
    ): Boolean {
        val localSubtitles = extractSubtitles(siblingFiles)
        audioViewModel.playAudio(fileBean, localSubtitles)
        if (navigateToDetail) {
            onNavigate(Route.MusicDetail)
        }
        return true
    }

    /**
     * 打开文本
     */
    suspend fun openText(fileBean: FileBean): Boolean {
        val settings = settingUiState()
        val txtSize = settings.txtSize.toIntOrNull() ?: 200
        if (fileBean.size.toLong() >= txtSize * 1024) {
            App.instance.toast("仅支持打开${txtSize}kb以下的文件")
            return false
        }

        val bytes = withContext(Dispatchers.IO) {
            var cached = fileViewModel.textFileCache[fileBean]
            if (cached == null) {
                runCatching {
                    val inputStream = fileRepository.getDownloadInputStream(fileBean.pickCode, fileBean.fileId)
                    if (inputStream != null) {
                        cached = inputStream.readBytes()
                        fileViewModel.textFileCache[fileBean] = cached
                    }
                }
            }
            cached
        }

        if (bytes != null) {
            withContext(Dispatchers.Main) {
                fileViewModel.textBodyByteArray = bytes
                onNavigate(Route.TxtReader(title = fileBean.name))
            }
            return true
        } else {
            App.instance.toast("文件加载失败！")
            return false
        }
    }

    /**
     * 打开网页
     */
    suspend fun openWeb(fileBean: FileBean): Boolean {
        val settings = settingUiState()
        val txtSize = settings.txtSize.toIntOrNull() ?: 200
        if (fileBean.size.toLong() >= txtSize * 1024) {
            App.instance.toast("仅支持打开${txtSize}kb以下的文件")
            return false
        }

        val bytes = withContext(Dispatchers.IO) {
            var cached = fileViewModel.textFileCache[fileBean]
            if (cached == null) {
                runCatching {
                    val inputStream = fileRepository.getDownloadInputStream(fileBean.pickCode, fileBean.fileId)
                    if (inputStream != null) {
                        cached = inputStream.readBytes()
                        fileViewModel.textFileCache[fileBean] = cached
                    }
                }
            }
            cached
        }

        if (bytes != null) {
            withContext(Dispatchers.Main) {
                fileViewModel.webBodyByteArray = bytes
                onNavigate(Route.HtmlWebViewScreen(title = fileBean.name))
            }
            return true
        } else {
            App.instance.toast("文件加载失败！")
            return false
        }
    }

    /**
     * 打开种子任务
     */
    fun openTorrent(fileBean: FileBean): Boolean {
        fileViewModel.getTorrentTask(fileBean.sha1)
        return true
    }

    /**
     * 打开压缩包
     */
    fun openZip(fileBean: FileBean): Boolean {
        val index = fileViewModel.fileBeanList.indexOfFirst { it.pickCode == fileBean.pickCode }
        if (index >= 0) {
            fileViewModel.selectIndex = index
        }
        fileViewModel.getZipListFile()
        return true
    }
}
