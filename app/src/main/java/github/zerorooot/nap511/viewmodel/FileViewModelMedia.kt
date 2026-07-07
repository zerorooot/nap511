package github.zerorooot.nap511.viewmodel

import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.service.Sha1Service
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
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
    viewModelScope.launch {
        val imageBean = fileService.image(
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
        fileBean.createTimeString = "▶️ $playTime% $createTimeString"

        val arrayListOf = arrayListOf<FileBean>()
        arrayListOf.addAll(fileBeanList)
        arrayListOf[index] = fileBean
        fileBeanList.clear()
        fileBeanList.addAll(arrayListOf)

        if (!isSearchState) {
            fileListCache[cid]?.fileBeanList = arrayListOf
        }

        val map = hashMapOf<String, String>()
        map["op"] = "update"
        map["pick_code"] = pickCode
        map["time"] = duration.toString()
        map["category"] = "1"
        map["format"] = "json"
        val videoHistory = fileService.videoHistory(map)
        XLog.d("更新视频时间 $videoHistory")
    }
}

internal fun FileViewModel.getVideoInfo(pickCode: String, fileBeanIndex: Int) {
    viewModelScope.launch {
        val video = fileService.video(pickCode).copy(index = fileBeanIndex)
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
    intent.putExtra("cookie", cookie)
    context.startService(intent)
}
