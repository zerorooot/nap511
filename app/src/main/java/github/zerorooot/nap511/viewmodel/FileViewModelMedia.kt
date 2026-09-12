package github.zerorooot.nap511.viewmodel

import android.content.Intent
import android.content.res.Configuration
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.ImageBean
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.service.Sha1Service
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.onFailureToastAndLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * FileViewModel 的扩展函数：媒体与文件查看相关
 */
@OptIn(ExperimentalCoilApi::class)
internal fun FileViewModel.getImage(fileBeanList: List<FileBean>, indexOf: Int) {
    if (indexOf !in fileBeanList.indices) return
    val fileBean = fileBeanList[indexOf]
    val pickCode = fileBean.pickCode
    if (pickCode.isEmpty()) return

    val cid = currentCid
    if (imageBeanCache[cid]?.containsKey(indexOf) == true) {
        return
    }

    // 优先检查 Coil 磁盘缓存：如果本地已经存在图片缓存，直接使用本地文件路径，避免发起 API 请求
    val diskCache = context.imageLoader.diskCache
    if (diskCache != null) {
        val localFile = diskCache.openSnapshot(pickCode)?.use { it.data.toFile() }
        if (localFile != null && localFile.exists()) {
            val localImageBean = ImageBean(
                url = localFile.absolutePath,
                fileName = fileBean.name,
                pickCode = pickCode
            )
            val oldMap = imageBeanCache[cid] ?: hashMapOf()
            val newMap = HashMap(oldMap)
            newMap[indexOf] = localImageBean
            imageBeanCache[cid] = newMap
            return
        }
    }

    val loadingKey = "$cid-$indexOf"
    synchronized(imageLoadingSet) {
        if (imageLoadingSet.contains(loadingKey)) return
        imageLoadingSet.add(loadingKey)
    }

    viewModelScope.launch {
        try {
            runCatching {
                val imageBean = fileRepository.image(
                    pickCode, System.currentTimeMillis() / 1000
                ).imageBean

                val oldMap = imageBeanCache[cid] ?: hashMapOf()
                val newMap = HashMap(oldMap)
                newMap[indexOf] = imageBean

                imageBeanCache[cid] = newMap
            }.onFailureToastAndLog()
        } finally {
            synchronized(imageLoadingSet) {
                imageLoadingSet.remove(loadingKey)
            }
        }
    }
}

internal fun FileViewModel.updateVideoFileBean(
    cid: String,
    index: Int,
    duration: Int,
    pickCode: String
) {
    viewModelScope.launch {
        val fileBean = fileBeanList[index]

        if (fileBean.isVideo != 1) return@launch

        val playTime = if (fileBean.playLong == 0.0) {
            100
        } else {
            ((duration.toFloat() / fileBean.playLong) * 100).roundToInt()
        }

        val createTimeString =
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                fileBean.createTime.toLong() * 1000
            )
        val newTimeString = "▶️ $playTime% $createTimeString"
        val updatedBean = fileBean.copy(createTimeString = newTimeString)
        fileBeanList[index] = updatedBean

        if (!isSearchState) {
            fileListCache[cid]?.fileBeanList = ArrayList(fileBeanList.toList())
        }

        val map = mapOf(
            "op" to "update",
            "pick_code" to pickCode,
            "time" to duration.toString(),
            "category" to "1",
            "format" to "json"
        )
        runCatching {
            val videoHistory = fileRepository.videoHistory(map)
            if (!videoHistory.state) {
                App.instance.toast(videoHistory.error)
                XLog.e("更新视频时间失败！ $videoHistory")
            } else {
                XLog.d("更新视频时间 $videoHistory")
            }
        }.onFailureToastAndLog()
    }
}

internal fun FileViewModel.getVideoInfo(pickCode: String, fileBeanIndex: Int, fileName: String) {
    viewModelScope.launch {
        val isAutoRotate = SettingsRepository.getDataSuspend(ConfigKeyUtil.AUTO_ROTATE, false)
        val videoLinkMode = SettingsRepository.getDataSuspend(ConfigKeyUtil.VIDEO_LINK_MODE, false)
        val autoJumpRetry = SettingsRepository.getDataSuspend(ConfigKeyUtil.AUTO_JUMP_RETRY, true)
        val hideLoading = SettingsRepository.getDataSuspend(ConfigKeyUtil.HIDE_LOADING_VIEW, false)

        runCatching {
            val video = if (videoLinkMode) {
                fileRepository.video(pickCode)
                    .copy(
                        index = fileBeanIndex,
                        isAutoRotate = isAutoRotate,
                        videoLinkMode = true,
                        autoJumpRetry = autoJumpRetry,
                        hideLoading = hideLoading
                    )
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
                    fileName = fileName,
                    pickCode = pickCode,
                    videoLinkMode = false,
                    autoJumpRetry = autoJumpRetry,
                    hideLoading = hideLoading,
                    videoUrl = "http://115.com/api/video/m3u8/${pickCode}.m3u8"
                )
            }
            XLog.d("FileViewModel getVideoInfo $video")
            _launchVideoEvent.emit(video)
        }.onFailureToastAndLog()
        setRefreshingStatus(false)
    }
}

internal fun FileViewModel.downloadSmallFile(
    fileBean: FileBean,
    onSuccess: (ByteArray) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        var bytes = textFileCache[fileBean]
        if (bytes == null) {
            runCatching {
                val downloadInputStream =
                    fileRepository.getDownloadInputStream(fileBean.pickCode, fileBean.fileId)
                if (downloadInputStream == null) {
                    setRefreshingStatus(false)
                    App.instance.toast("文件加载失败！")
                    return@launch
                }
                bytes = downloadInputStream.readBytes()
                textFileCache[fileBean] = bytes
            }.onFailureToastAndLog()
        }
        if (bytes != null) {
            setRefreshingStatus(false)
            onSuccess(bytes)
        } else {
            setRefreshingStatus(false)
        }
    }
}

internal fun FileViewModel.downloadText(fileBean: FileBean, onNav: (Route) -> Unit) {
    downloadSmallFile(fileBean) { bytes ->
        textBodyByteArray = bytes
        onNav.invoke(Route.TxtReader)
    }
}

internal fun FileViewModel.downloadWeb(fileBean: FileBean, onNav: (Route) -> Unit) {
    downloadSmallFile(fileBean) { bytes ->
        webBodyByteArray = bytes
        onNav.invoke(Route.HtmlWebViewScreen)
    }
}

internal fun FileViewModel.startSendAria2Service(index: Int) {
    val fileBean = fileBeanList[index]
    if (fileBean.isFolder) {
        App.instance.toast("暂时无法下载文件夹")
        return
    }
    val intent = Intent(context, Sha1Service::class.java)
    intent.putExtra(ConfigKeyUtil.COMMAND, ConfigKeyUtil.SENT_TO_ARIA2)
    intent.putExtra("list", Gson().toJson(fileBean))
    context.startService(intent)
}
