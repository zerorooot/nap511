package github.zerorooot.nap511.viewmodel

import android.content.Intent
import android.content.res.Configuration
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.service.Sha1Service
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * FileViewModel 的扩展函数：媒体与文件查看相关
 */
internal fun FileViewModel.getImage(fileBeanList: List<FileBean>, indexOf: Int) {
    if (imageBeanCache.containsKey(currentCid) && imageBeanCache[currentCid]!!.containsKey(
            indexOf
        )
    ) {
        return
    }

    //获取当前点击的
    viewModelScope.launch(exceptionHandler) {
        val imageBean = fileRepository.image(
            fileBeanList[indexOf].pickCode, System.currentTimeMillis() / 1000
        ).imageBean

        val oldMap = imageBeanCache[currentCid] ?: hashMapOf()
        val newMap = HashMap(oldMap)
        newMap[indexOf] = imageBean

        imageBeanCache[currentCid] = newMap
    }
}

internal fun FileViewModel.updateVideoFileBean(
    cid: String,
    index: Int,
    duration: Int,
    pickCode: String
) {
    viewModelScope.launch(exceptionHandler) {
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
        val videoHistory = fileRepository.videoHistory(map)
        XLog.d("更新视频时间 $videoHistory")
    }
}

val isAutoRotate by lazy {
    DataStoreUtil.getData(ConfigKeyUtil.AUTO_ROTATE, false)
}

internal fun FileViewModel.getVideoInfo(pickCode: String, fileBeanIndex: Int) {
    viewModelScope.launch(exceptionHandler) {
        //仅开启自动旋转才请求，以提升视频打开速度
        val video = if (isAutoRotate) {
            fileRepository.video(pickCode).copy(index = fileBeanIndex, isAutoRotate = true)
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
                videoUrl = "http://115.com/api/video/m3u8/${pickCode}.m3u8"
            )
        }

        _launchVideoEvent.emit(video)
        setRefreshingStatus(false)
    }
}

internal fun FileViewModel.downloadText(fileBean: FileBean) {
    viewModelScope.launch(Dispatchers.IO) {
        var bytes = textFileCache[fileBean]
        if (bytes == null) {
            bytes = fileRepository.getDownloadInputStream(fileBean.pickCode, fileBean.fileId)
                .readBytes()
            textFileCache[fileBean] = bytes
        }
        textBodyByteArray = bytes
//        isOpenTextBodyDialog = true
        openTextBodyDialog()
        setRefreshingStatus(false)
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
