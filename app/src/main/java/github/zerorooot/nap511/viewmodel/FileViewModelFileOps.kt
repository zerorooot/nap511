package github.zerorooot.nap511.viewmodel

import android.annotation.SuppressLint
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.RenameBean
import github.zerorooot.nap511.util.App
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * FileViewModel 的扩展函数：文件操作（创建、删除、重命名、剪切、文件信息）
 */
internal fun FileViewModel.cut(index: Int = -1) {
    cutFileList = if (index == -1) {
        fileBeanList.filter { i -> i.isSelect }
    } else {
        select(index)
        arrayListOf(fileBeanList[index])
    }
    isCutState = true
    recoverFromLongPress()
    unSelect()
}

internal fun FileViewModel.cancelCut() {
    unSelect()
    isCutState = false
    cutFileList = emptyList()
}

internal fun FileViewModel.removeFile() {
    if (cutFileList.isEmpty()) {
        isCutState = false
        return
    }
    //提前保存cid,防止进入其他文件夹后刷新当前目录
    val tempCid = currentCid
    isCutState = false

    val cid = cutFileList[0].let { if (it.isFolder) it.parentId else it.categoryId }
    if (cid == tempCid) {
        App.instance.toast("禁止原地移动～")
        return
    }

    viewModelScope.launch(exceptionHandler) {
        val move = fileRepository.removeFile(tempCid, cutFileList)
        val message = if (move.state) {
            cutFileList.forEach { i -> i.copy(isSelect = false) }
            //移除之前目录下剪切的文件
            fileListCache[cid]?.fileBeanList?.removeAll(cutFileList.toSet())
            //移除被剪切文件夹的缓存，防止路径未更改
            cutFileList.forEach { i ->
                if (i.isFolder) {
                    fileListCache.remove(i.categoryId)
                }
            }

            refresh(tempCid)
            "移动${cutFileList.size}个文件成功"
        } else {
            "移动失败~"
        }
        App.instance.toast(message)
    }
}

internal fun FileViewModel.createFolder(folderName: String) {
    viewModelScope.launch {
        //提前保存cid,防止进入其他文件夹后刷新当前目录
        val cid = currentCid
        val createFolder = fileRepository.createFolder(cid, folderName)
        val message = if (createFolder.state) {
            refresh(cid)
            "创建文件夹 $folderName 成功"
        } else {
            "创建失败，${createFolder.error}"
        }
        App.instance.toast(message)
    }
}

internal fun FileViewModel.getFileInfo(index: Int) {
    viewModelScope.launch {
        _isRefreshing.value = true
        val fileBean = fileBeanList[index]
        fileInfo = if (fileBean.isFolder) {
            fileRepository.getFileInfo(fileBean.categoryId)
        } else {
            fileRepository.getFileInfo(fileBean.fileId)
        }
        _isRefreshing.value = false
        openFileInfoDialog()
    }
}

internal fun FileViewModel.delete(index: Int) {
    val fileBean = fileBeanList[index]
    viewModelScope.launch(exceptionHandler) {
        val beforeList = fileBeanList
        val beforeFileListCache = fileListCache[currentCid]
        val beforeClickMap = clickMap.getOrDefault(currentCid, 0)
        val beforeImageBeanCache = imageBeanCache.getOrDefault(currentCid, hashMapOf())

        // XLog.d("FileViewModel.delete before fileListCache size ${fileListCache.size}")
        //提前删除，优化速度
        fileBeanList.remove(fileBean)
        fileListCache[currentCid]!!.fileBeanList.remove(fileBean)
        clickMap[currentCid] = clickMap.getOrDefault(currentCid, 0) - 1

        //删除文件夹内的文件夹
        if (fileBean.isFolder) {
            removeFolderCacheRecursively(fileBean.categoryId)
        }

        //    XLog.d("FileViewModel.delete after fileListCache size ${fileListCache.size}")
        //delete image bean
        imageBeanCache[currentCid]?.remove(index)

        val fid = fileBean.fileId
        val pid = currentCid

        val delete = fileRepository.delete(pid, fid)

        val message = if (delete.state) {
            "删除 ${fileBean.name} 成功"
        } else {
            fileBeanList = beforeList
            fileListCache[currentCid] = beforeFileListCache!!
            clickMap[currentCid] = beforeClickMap
            imageBeanCache[currentCid] = beforeImageBeanCache
            "删除 ${fileBean.name} 失败~${delete.errorMsg}"
        }
        App.instance.toast(message)
    }
}

internal fun FileViewModel.rename(name: String) {
    viewModelScope.launch(exceptionHandler) {
        val cid = currentCid
        val fileBean = fileBeanList[selectIndex]
        val beforeList = fileBeanList
        val beforeFileListCache = fileListCache[cid]
        //提前重命名，提升相应速度
        fileBeanList[selectIndex] = fileBean.copy(name = name)
        fileListCache[cid]!!.fileBeanList[selectIndex] = fileBean.copy(name = name)
        fileListCache[fileBean.categoryId]?.let { it.path.last().name = name }
        val rename = fileRepository.rename(RenameBean(fileBean.fileId, name).toRequestBody())
        val message = if (rename.state) {
            "重命名成功"
        } else {
            fileBeanList = beforeList
            fileListCache[cid] = beforeFileListCache!!
            "重命名失败"
        }
        App.instance.toast(message)
    }
}

internal fun FileViewModel.deleteMultiple() {
    viewModelScope.launch(exceptionHandler) {
        val cid = currentCid
        val beforeList = fileBeanList
        val beforeFileListCache = fileListCache[cid]
        val beforeClickMap = clickMap.getOrDefault(cid, 0)

        //  XLog.d("FileViewModel.deleteMultiple before fileListCache size ${fileListCache.size}")

        val mapOf = hashMapOf<String, String>()
        mapOf["ignore_warn"] = "1"
        mapOf["pid"] = cid
        val filter = fileBeanList.filter { i -> i.isSelect }
        filter.forEachIndexed { index: Int, fileBean: FileBean ->
            mapOf["fid[$index]"] = fileBean.fileId
            //update image cache
            imageBeanCache[cid]?.remove(index)
            if (fileBean.isFolder) {
                removeFolderCacheRecursively(fileBean.categoryId)
            }
        }
        //提前删除，优化速度
        fileBeanList.removeAll(filter)
        fileListCache[cid]!!.fileBeanList = ArrayList(fileBeanList)
        clickMap[cid] = clickMap.getOrDefault(cid, 0) - filter.size

        //  XLog.d("FileViewModel.deleteMultiple after fileListCache size ${fileListCache.size}")

        recoverFromLongPress()

        val deleteMultiple = fileRepository.deleteMultiple(mapOf)
        val message = if (deleteMultiple.state) {
            "成功删除 ${filter.size} 个文件"
        } else {
            fileBeanList = beforeList
            fileListCache[cid] = beforeFileListCache!!
            clickMap[cid] = beforeClickMap
            "删除 ${filter.size} 个文件失败~"
        }
        App.instance.toast(message)
    }
}

// 1. 静态提取扩展名集合，避免每次遍历重复构造数组
private val ZIP_EXTS = setOf("rar", "tar", "gz", "7z", "zip", "part", "jar")
private val IMG_EXTS = setOf("gif", "jpg", "png", "jpeg", "bmp", "tif", "svg", "pic", "heic", "dng", "webp")
private val AUDIO_EXTS = setOf(
    "mp3", "wma", "wav", "midi", "flac", "ram", "ra", "mid", "aac", "m4a", "ape", "au",
    "ogg", "aif", "aiff", "snd", "voc", "mpa", "cda", "vqf", "wvx", "wmx", "m3u", "m3u8",
    "ttbl", "ttpl", "tta", "tak", "mpc", "mp+", "mp3pro", "mp1", "mp2", "mac", "xm",
    "umx", "stm", "s3m", "mtm", "mod", "it", "far", "rmi", "fla", "dts", "dtswav", "awb"
)
private val TXT_EXTS = setOf(
    "doc", "docx", "xls", "pdf", "ppt", "wps", "dps", "et", "mdb", "reg", "txt", "wri",
    "rtf", "lrc", "vob", "sub", "srt", "ass", "ssa", "idx", "umd", "xlsx", "xlsm", "xltx",
    "xltm", "xlam", "xlsb", "odt", "pptx", "ods", "odp", "chm", "pot", "pps", "ppsx",
    "smi", "vtt", "stl", "sbv", "ttml", "ksc", "snc", "krc", "c", "cpp", "h", "asm",
    "s", "java", "o", "asp", "aspx", "bat", "bas", "prg", "cmd", "log", "php", "js",
    "go", "sh", "css", "scss", "sass", "less", "class", "hpp", "cc", "hex", "hxx",
    "cxx", "c++", "cs", "py", "pl", "pm", "md", "cue", "utf", "dpt", "ofd", "eto",
    "ets", "mhtml", "mht", "uof", "dot", "wpt", "dotx", "docm", "dotm", "ett", "xlt",
    "pptm", "ppsm", "potx", "potm", "csv", "xml", "html", "htm"
)
// 2. 改造函数：入参和返回值均为 List，利用 .map() 生成全新的不可变列表
internal fun FileViewModel.formatFileBeanList(fileBeanList: List<FileBean>): ArrayList<FileBean> {
    // 复用同一个 SimpleDateFormat 实例
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    return fileBeanList.map { fileBean ->
        // 解析时间戳
        val updateTimeString = fileBean.updateTime.toLongOrNull()?.let {
            dateFormat.format(it * 1000)
        } ?: ""

        var createTimeString = fileBean.createTime.toLongOrNull()?.let {
            dateFormat.format(it * 1000)
        } ?: ""

        // 判断是否为文件夹
        val isFolder = fileBean.fileId.isEmpty()
        val finalFileId = if (isFolder) fileBean.categoryId else fileBean.fileId

        var sizeString = fileBean.sizeString
        var modifiedTimeString = fileBean.modifiedTimeString
        var rawModifiedTime = fileBean.modifiedTime

        if (isFolder) {
            modifiedTimeString = fileBean.modifiedTime.toLongOrNull()?.let {
                dateFormat.format(it * 1000)
            } ?: ""
        } else {
            sizeString = fileRepository.formatFileSize(fileBean.size.toLongOrNull() ?: 0) + " "
            modifiedTimeString = fileBean.modifiedTime

            if (fileBean.modifiedTime.isDigitsOnly()) {
                val parsedTime = runCatching {
                    dateFormat.parse(fileBean.modifiedTime)?.time?.div(1000)
                }.getOrNull()
                if (parsedTime != null) {
                    rawModifiedTime = parsedTime.toString()
                }
            }

            if (fileBean.currentPlayTime != 0 && fileBean.playLong != 0.00) {
                val playTime = ((fileBean.currentPlayTime.toFloat() / fileBean.playLong) * 100).roundToInt()
                createTimeString = "▶️ $playTime% $createTimeString"
            }
        }

        // 图标与时长处理
        var playLongString = fileBean.playLongString
        val icoRes = when {
            isFolder -> R.drawable.folder
            fileBean.isVideo == 1 -> {
                playLongString = generateTime(fileBean.playLong.toLong()) + " "
                R.drawable.mp4
            }
            fileBean.icoString in ZIP_EXTS -> R.drawable.zip
            fileBean.icoString in IMG_EXTS -> R.drawable.png
            fileBean.icoString in TXT_EXTS -> R.drawable.txt
            fileBean.icoString in AUDIO_EXTS -> {
                playLongString = generateTime(fileBean.playLong.toLong()) + " "
                R.drawable.mp3
            }
            fileBean.icoString == "apk" -> R.drawable.apk
            fileBean.icoString == "iso" -> R.drawable.iso
            fileBean.icoString == "torrent" -> R.drawable.torrent
            else -> fileBean.fileIco
        }

        // 使用 copy() 拷贝并返回更新后的不可变对象
        fileBean.copy(
            fileId = finalFileId,
            isFolder = isFolder,
            fileIco = icoRes,
            updateTimeString = updateTimeString,
            createTimeString = createTimeString,
            modifiedTimeString = modifiedTimeString,
            modifiedTime = rawModifiedTime,
            sizeString = sizeString,
            playLongString = playLongString
        )
    }.toMutableList() as ArrayList<FileBean>
}

@SuppressLint("DefaultLocale")
private fun generateTime(totalSeconds: Long): String {
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60 % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) String.format(
        "%02d:%02d:%02d", hours, minutes, seconds
    ) else String.format("%02d:%02d", minutes, seconds)
}