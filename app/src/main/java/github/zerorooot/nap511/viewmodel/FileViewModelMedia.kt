package github.zerorooot.nap511.viewmodel

import android.content.Intent
import android.content.res.Configuration
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.ImageBean
import github.zerorooot.nap511.bean.LaunchVideoParams
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SubtitleItem
import github.zerorooot.nap511.bean.SubtitleSourceType
import github.zerorooot.nap511.bean.VideoAttributeBean
import github.zerorooot.nap511.bean.VideoBean
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.service.Sha1Service
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.getCoilCacheUrl
import github.zerorooot.nap511.util.onFailureToastAndLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * FileViewModel 的扩展函数：媒体与文件查看相关
 */
internal fun FileViewModel.getImage(fileBean: FileBean) {
    val pickCode = fileBean.pickCode
    val cid = currentCid

    if (pickCode.isEmpty()) {
        XLog.w("FileViewModel.getImage pickCode is empty: fileBean=${fileBean.name}")
        return
    }

    if (imageBeanCache[cid]?.containsKey(pickCode) == true) {
        XLog.d(
            "FileViewModel.getImage hit imageBeanCache: fileBean=${fileBean.name}, cid=$cid, cache imageBean=${
                imageBeanCache[cid]?.get(
                    pickCode
                )
            }"
        )
        return
    }

    // 优先匹配 Coil 缓存（按 MemoryCache -> DiskCache 顺序短路判断，在内存时无需磁盘 I/O）
    val cachedUrl = getCoilCacheUrl(context.imageLoader, pickCode)
    if (cachedUrl != null) {
        XLog.d("FileViewModel.getImage hit Coil cache: fileBean=${fileBean.name}, cachedUrl=$cachedUrl")
        val cachedImageBean = ImageBean(
            url = cachedUrl, fileName = fileBean.name, pickCode = pickCode
        )
        val oldMap = imageBeanCache[cid] ?: hashMapOf()
        val newMap = HashMap(oldMap)
        newMap[pickCode] = cachedImageBean
        imageBeanCache[cid] = newMap
        return
    }

    val loadingKey = "$cid-$pickCode"
    synchronized(imageLoadingSet) {
        if (imageLoadingSet.contains(loadingKey)) {
            XLog.d("FileViewModel.getImage already in imageLoadingSet: fileBean=${fileBean.name}, loadingKey=$loadingKey")
            return
        }
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
                newMap[pickCode] = imageBean

                imageBeanCache[cid] = newMap
                XLog.d("FileViewModel.getImage request success: fileBean=${fileBean.name}, imageBean=$imageBean")
            }.onFailureToastAndLog()
        } finally {
            synchronized(imageLoadingSet) {
                imageLoadingSet.remove(loadingKey)
            }
        }
    }
}

/**
 * 批量更新视频播放进度与历史记录
 * @param cid 当前目录 ID
 * @param videoHistoryMap 包含 pickCode 与对应 VideoBean 的映射表
 */
internal fun FileViewModel.updateVideoFileBeans(
    cid: String, videoHistoryMap: Map<String, VideoBean>
) {
    if (videoHistoryMap.isEmpty()) return

    viewModelScope.launch {
        // 构建时间格式化工具，避免在循环体内重复实例化
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        var isAnyUpdated = false

        // 批量更新本地内存中的列表数据
        videoHistoryMap.forEach { (pickCode, bean) ->
            val index = fileBeanList.indexOfFirst { it.pickCode == pickCode }
            // 防御越界保护
            if (index == -1) return@forEach

            val fileBean = fileBeanList[index]
            if (fileBean.isVideo != 1) return@forEach

            val duration = bean.currentDuration
            val playTime = if (fileBean.playLong == 0.0) {
                100
            } else {
                ((duration.toFloat() / fileBean.playLong) * 100).roundToInt()
            }

            val playTimeRatio = "▶️ $playTime%"
            val updatedBean = fileBean.copy(playLongRatio = playTimeRatio)

            fileBeanList[index] = updatedBean
            isAnyUpdated = true
        }

        //本地列表全部修改完成后，仅同步一次缓存，避免频繁拷贝与多次刷新
        if (isAnyUpdated && !isSearchState) {
            fileListCache[cid]?.fileBeanList = ArrayList(fileBeanList.toList())
        }

//        // 并发发起所有网络请求（async + awaitAll）
//        val uploadJobs = videoHistoryMap.map { (pickCode, bean) ->
//            async {
//                val map = mapOf(
//                    "op" to "update",
//                    "pick_code" to pickCode,
//                    "time" to bean.currentDuration.toString(),
//                    "category" to "1",
//                    "format" to "json"
//                )
//                runCatching {
//                    val videoHistory = fileRepository.videoHistory(map)
//                    if (!videoHistory.state) {
//                        XLog.e("更新视频时间失败！ name: ${bean.name}, pickCode: $pickCode, result: $videoHistory")
//                    } else {
//                        XLog.d("更新视频时间成功 name: ${bean.name}, pickCode: $pickCode, result: $videoHistory")
//                    }
//                }.onFailure { e ->
//                    XLog.e("更新视频时间异常 name: ${bean.name}, pickCode: $pickCode", e)
//                }
//            }
//        }
//
//        // 等待所有请求完成
//        uploadJobs.awaitAll()
    }
}

private fun isSubtitleFile(fileName: String): Boolean {
    val SUPPORTED_SUBTITLE_EXTS = setOf("srt", "vtt", "ass", "ssa", "sub", "lrc")
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
    return SUPPORTED_SUBTITLE_EXTS.contains(ext)
}

internal fun FileViewModel.getLocalSubtitleList(): List<SubtitleItem> {
    return fileBeanList.filter { fileBean ->
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
}

internal fun FileViewModel.getVideoInfo(fileBean: FileBean) {
    val pickCode = fileBean.pickCode
    val fileName = fileBean.name
    val videoList = fileBeanList.filter { it.isVideo == 1 && it.playLong != 0.0 }
        .map { VideoBean(name = it.name, pickCode = it.pickCode, fileId = fileBean.fileId) }
    val fileBeanIndex = videoList.indexOfFirst { it.pickCode == pickCode }

    val localSubtitleList = getLocalSubtitleList()



    viewModelScope.launch {
        runCatching {
            val videoAttributeBean = VideoAttributeBean(
                isAutoRotate = settingUiState.autoRotateEnabled,
                videoLinkMode = settingUiState.videoLinkMode,
                autoJumpRetry = settingUiState.autoJumpRetry,
                hideLoading = settingUiState.hideLoadingView
            )
            val video = if (settingUiState.videoLinkMode) {
                fileRepository.video(pickCode).copy(
                    index = fileBeanIndex,
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
                    videoUrl = "http://115.com/api/video/m3u8/${pickCode}.m3u8"
                )
            }
            XLog.d("FileViewModel getVideoInfo $video")
            val launchVideoParams = LaunchVideoParams(
                videoInfo = video,
                videoAttribute = videoAttributeBean,
                localSubtitleItem = localSubtitleList,
                videoList = videoList,
                categoryId = fileBean.categoryId
            )
            _launchVideoEvent.emit(launchVideoParams)
        }.onFailureToastAndLog()
        setRefreshingStatus(false)
    }
}

internal fun FileViewModel.downloadSmallFile(
    fileBean: FileBean, onSuccess: (ByteArray) -> Unit
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
            withContext(Dispatchers.Main) {
                onSuccess(bytes)
            }
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
