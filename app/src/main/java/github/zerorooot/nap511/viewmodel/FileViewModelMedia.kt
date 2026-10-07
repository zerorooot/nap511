package github.zerorooot.nap511.viewmodel

import android.content.Intent
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.ImageBean
import github.zerorooot.nap511.bean.VideoBean
import github.zerorooot.nap511.service.Sha1Service
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.util.getCoilCacheUrl
import github.zerorooot.nap511.util.onFailureToastAndLog
import kotlinx.coroutines.launch
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
 * 处理 VideoActivity 返回的播放历史与定位结果
 */
fun FileViewModel.onVideoActivityResult(
    pickCode: String,
    cid: String,
    videoHistoryMap: Map<String, VideoBean>
) {
    if (videoHistoryMap.isNotEmpty()) {
        updateVideoFileBeans(cid, videoHistoryMap)
    }
    if (pickCode.isNotEmpty()) {
        viewModelScope.launch {
            val fileList = FileCacheManager[cid]?.fileBeanList
            val index = fileList?.indexOfFirst { it.pickCode == pickCode } ?: -1
            if (index >= 0) {
                clickMap[cid] = index
                //可能被终端页面影响。所以只有在当前目录才滚动。
                // 无法更新非当前页的位置，未被布局，不知道非当前页面的 firstVisibleItemIndex 和 firstVisibleItemScrollOffset
                if (cid == currentCid) {
                    _videoResultEvent.tryEmit(index)
                }
            }
        }

    }
}

/**
 * 批量更新视频播放进度与历史记录
 * @param cid 当前 ID
 * @param videoHistoryMap 包含 pickCode 与对应 VideoBean 的映射表
 */
internal fun FileViewModel.updateVideoFileBeans(
    cid: String, videoHistoryMap: Map<String, VideoBean>
) {
    if (videoHistoryMap.isEmpty()) return
    viewModelScope.launch {
        //可能在终端使用，而终端不一定是当前的文件list,所以从缓存里取file list
        val fileList = FileCacheManager[cid]?.fileBeanList ?: return@launch
        // 批量更新本地内存中的列表数据
        videoHistoryMap.forEach { (pickCode, bean) ->
            val index = fileList.indexOfFirst { it.pickCode == pickCode }
            // 防御越界保护
            if (index == -1) return@forEach

            val fileBean = fileList[index]
            if (fileBean.isVideo != 1) return@forEach

            val duration = bean.currentDuration
            val playTime = if (fileBean.playLong == 0.0) {
                100
            } else {
                ((duration.toFloat() / fileBean.playLong) * 100).roundToInt()
            }

            val playTimeRatio = "▶️ $playTime%"
            val updatedBean = fileBean.copy(playLongRatio = playTimeRatio)

            fileList[index] = updatedBean
        }

        //本地列表全部修改完成后，仅同步一次缓存，避免频繁拷贝与多次刷新
        if (!isSearchState) {
            FileCacheManager[cid]?.fileBeanList = ArrayList(fileList.toList())
        }
        //在FileScreen中刷新
        if (cid == currentCid) {
            fileBeanList.clear()
            fileBeanList.addAll(fileList)
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
