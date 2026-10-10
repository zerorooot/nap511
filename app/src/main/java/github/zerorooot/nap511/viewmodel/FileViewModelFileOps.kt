package github.zerorooot.nap511.viewmodel

import android.annotation.SuppressLint
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.RenameBean
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.util.deleteCoilCache
import github.zerorooot.nap511.util.formatFileSize
import github.zerorooot.nap511.util.onFailureToastAndLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * FileViewModel 的扩展函数：文件操作（创建、删除、重命名、剪切、文件信息）
 */
internal fun FileViewModel.cut(index: Int = -1) {
    val itemsToCut = if (index == -1) {
        fileBeanList.filter { it.isSelect }
    } else {
        fileBeanList.getOrNull(index)?.let { listOf(it) } ?: emptyList()
    }
    updateContentState {
        copy(
            isCutState = true,
            cutFileList = itemsToCut,
            isLongClickState = false,
            fileBeanList = fileBeanList.map { it.copy(isSelect = false) },
            appBarTitle = if (isSearchState) "搜索" else context.getString(R.string.app_name)
        )
    }
}

internal fun FileViewModel.cancelCut() {
    updateContentState {
        copy(
            isCutState = false,
            cutFileList = emptyList()
        )
    }
}

internal fun FileViewModel.removeFile() {
    val cutList = contentState.value.cutFileList
    if (cutList.isEmpty()) {
        updateContentState { copy(isCutState = false) }
        return
    }

    val targetCid = currentCid
    updateContentState { copy(isCutState = false) }

    val fileCid = cutList[0].let { if (it.isFolder) it.parentId else it.categoryId }
    if (fileCid == targetCid) {
        App.instance.toast("禁止原地移动～")
        return
    }

    setRefreshingStatus(true)
    viewModelScope.launch {
        runCatching {
            val move = fileRepository.removeAllFile(targetCid, cutList)
            if (move.state) {
                // 统一委托 FileCacheManager 移除剪切的文件并清理子文件夹缓存
                FileCacheManager.removeItems(fileCid, cutList.map { it.fileId })
                updateContentState { copy(cutFileList = emptyList()) }
                refresh(targetCid)
                "移动${cutList.size}个文件成功"
            } else {
                "移动失败~"
            }
        }.onSuccess { message ->
            App.instance.toast(message)
        }.onFailureToastAndLog()
    }
}

internal fun FileViewModel.createFolder(folderName: String) {
    viewModelScope.launch {
        //提前保存cid,防止进入其他文件夹后刷新当前目录
        val cid = currentCid
        runCatching {
            val createFolder = fileRepository.createFolder(cid, folderName)
            if (createFolder.state) {
                refresh(cid)
                "创建文件夹 $folderName 成功"
            } else {
                "创建失败，${createFolder.error}"
            }
        }.onSuccess { message ->
            App.instance.toast(message)
        }.onFailureToastAndLog()
    }
}

internal fun FileViewModel.getFileInfo(fileBean: FileBean) {
    viewModelScope.launch {
        setRefreshingStatus(true)
        runCatching {
            val info = if (fileBean.isFolder) {
                fileRepository.getFileInfo(fileBean.categoryId)
            } else {
                fileRepository.getFileInfo(fileBean.fileId)
            }
            XLog.d("file ${fileBean.name} fileInfo $info ; file bean $fileBean")
            updateDialogState {
                copy(
                    fileInfo = info,
                    targetFileBean = fileBean,
                    activeDialog = FileDialogState.FileInfo
                )
            }
        }.onFailureToastAndLog()
        setRefreshingStatus(false)
    }
}

@OptIn(ExperimentalCoilApi::class)
internal fun FileViewModel.delete(fileBean: FileBean) {
    viewModelScope.launch {
        val cid = currentCid
        val beforeList = fileBeanList
        val beforeClickMap = clickMap.getOrDefault(cid, 0)

        // 提前更新内存不可变列表，优化交互响应速度并取得安全撤销句柄
        updateContentState {
            copy(fileBeanList = fileBeanList.filterNot { it.fileId == fileBean.fileId })
        }
        val rollback = FileCacheManager.removeItem(cid, fileBean.fileId, fileBean.isFolder)
        clickMap[cid] = clickMap.getOrDefault(cid, 0) - 1

        // delete image bean
        imageBeanCache[cid]?.remove(fileBean.pickCode)

        val fid = fileBean.fileId
        val pid = cid

        runCatching {
            val delete = fileRepository.delete(pid, fid)
            if (delete.state) {
                // 删除coil图片缓存
                if (fileBean.photoThumb.isNotEmpty()) {
                    context.imageLoader.deleteCoilCache(fileBean.pickCode)
                }
                "删除 ${fileBean.name} 成功"
            } else {
                updateContentState { copy(fileBeanList = beforeList) }
                rollback?.rollback()
                clickMap[cid] = beforeClickMap
                "删除 ${fileBean.name} 失败~${delete.errorMsg}"
            }
        }.onSuccess { message ->
            App.instance.toast(message)
        }.onFailureToastAndLog()
    }
}

internal fun FileViewModel.rename(fileBean: FileBean? = dialogState.value.targetFileBean, name: String) {
    val targetBean = fileBean ?: dialogState.value.targetFileBean ?: return
    viewModelScope.launch {
        val cid = currentCid
        val beforeList = fileBeanList
        // 提前在不可变列表替换，提升响应速度并获取回滚闭包
        updateContentState {
            copy(
                fileBeanList = fileBeanList.map {
                    if (it.fileId == targetBean.fileId) it.copy(name = name) else it
                }
            )
        }
        val rollback = FileCacheManager.renameItem(cid, targetBean.fileId, name)

        runCatching {
            val rename = fileRepository.rename(RenameBean(targetBean.fileId, name).toRequestBody())
            if (rename.state) {
                "重命名成功"
            } else {
                updateContentState { copy(fileBeanList = beforeList) }
                rollback?.rollback()
                "重命名失败"
            }
        }.onSuccess { message ->
            App.instance.toast(message)
        }.onFailureToastAndLog()
    }
}

@OptIn(ExperimentalCoilApi::class)
internal fun FileViewModel.deleteMultiple() {
    viewModelScope.launch {
        val cid = currentCid
        val beforeList = fileBeanList
        val beforeClickMap = clickMap.getOrDefault(cid, 0)

        val mapOf = hashMapOf<String, String>()
        mapOf["ignore_warn"] = "1"
        mapOf["pid"] = cid
        val selectedFiles = fileBeanList.filter { it.isSelect }
        if (selectedFiles.isEmpty()) return@launch

        selectedFiles.forEachIndexed { index: Int, fileBean: FileBean ->
            mapOf["fid[$index]"] = fileBean.fileId
            // update image cache
            imageBeanCache[cid]?.remove(fileBean.pickCode)
            if (fileBean.photoThumb.isNotEmpty()) {
                context.imageLoader.deleteCoilCache(fileBean.pickCode)
            }
        }
        // 提前删除并获取批量回滚闭包
        val selectedIds = selectedFiles.map { it.fileId }.toSet()
        updateContentState {
            copy(
                fileBeanList = fileBeanList.filterNot { it.fileId in selectedIds },
                isLongClickState = false,
                appBarTitle = if (isSearchState) "搜索" else context.getString(R.string.app_name)
            )
        }
        val rollback = FileCacheManager.removeItems(cid, selectedFiles.map { it.fileId })
        clickMap[cid] = clickMap.getOrDefault(cid, 0) - selectedFiles.size

        runCatching {
            val deleteMultiple = fileRepository.deleteMultiple(mapOf)
            if (deleteMultiple.state) {
                "成功删除 ${selectedFiles.size} 个文件"
            } else {
                updateContentState { copy(fileBeanList = beforeList) }
                rollback?.rollback()
                clickMap[cid] = beforeClickMap
                "删除 ${selectedFiles.size} 个文件失败~"
            }
        }.onSuccess { message ->
            App.instance.toast(message)
        }.onFailureToastAndLog()
    }
}

// 1. 静态提取扩展名集合，避免每次遍历重复构造数组
internal val ZIP_EXTS = setOf("rar", "tar", "gz", "7z", "zip", "part", "jar")
internal val IMG_EXTS =
    setOf("gif", "jpg", "png", "jpeg", "bmp", "tif", "svg", "pic", "heic", "dng", "webp")
internal val AUDIO_EXTS = setOf(
    "mp3", "wma", "wav", "midi", "flac", "ram", "ra", "mid", "aac", "m4a", "ape", "au",
    "ogg", "aif", "aiff", "snd", "voc", "mpa", "cda", "vqf", "wvx", "wmx", "m3u", "m3u8",
    "ttbl", "ttpl", "tta", "tak", "mpc", "mp+", "mp3pro", "mp1", "mp2", "mac", "xm",
    "umx", "stm", "s3m", "mtm", "mod", "it", "far", "rmi", "fla", "dts", "dtswav", "awb"
)
internal val TXT_EXTS = setOf(
    "doc", "docx", "xls", "pdf", "ppt", "wps", "dps", "et", "mdb", "reg", "txt", "wri",
    "rtf", "lrc", "vob", "sub", "srt", "ass", "ssa", "idx", "umd", "xlsx", "xlsm", "xltx",
    "xltm", "xlam", "xlsb", "odt", "pptx", "ods", "odp", "chm", "pot", "pps", "ppsx",
    "smi", "vtt", "stl", "sbv", "ttml", "ksc", "snc", "krc", "c", "cpp", "h", "asm",
    "s", "java", "o", "asp", "aspx", "bat", "bas", "prg", "cmd", "log", "php", "js",
    "go", "sh", "css", "scss", "sass", "less", "class", "hpp", "cc", "hex", "hxx",
    "cxx", "c++", "cs", "py", "pl", "pm", "md", "cue", "utf", "dpt", "ofd", "eto",
    "ets", "mhtml", "mht", "uof", "dot", "wpt", "dotx", "docm", "dotm", "ett", "xlt",
    "pptm", "ppsm", "potx", "potm", "csv", "xml", "url"
)
internal val HTML_EXTS = setOf(
    "html", "htm"
)
internal val VIDEO_EXTS = setOf(
    "mp4", "mkv", "avi", "flv", "mov", "rmvb", "wmv", "m4v", "webm", "ts"
)

// 2. 改造函数：入参和返回值均为 List，利用 .map() 生成全新的不可变列表
fun formatFileBeanList(fileBeanList: List<FileBean>): ArrayList<FileBean> {
    // 复用同一个 SimpleDateFormat 实例
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    return fileBeanList.map { fileBean ->
        // 解析时间戳
        val updateTimeString = fileBean.updateTime.toLongOrNull()?.let {
            dateFormat.format(it * 1000)
        } ?: ""

        val createTimeString = fileBean.createTime.toLongOrNull()?.let {
            dateFormat.format(it * 1000)
        } ?: ""
        var playLongRatio = ""


        // 判断是否为文件夹
        val isFolder = fileBean.fileId.isEmpty() || fileBean.isFolder
        val finalFileId = if (isFolder) fileBean.categoryId else fileBean.fileId
        val finalParentId = if (isFolder) fileBean.parentId else fileBean.categoryId

        var sizeString = fileBean.sizeString
        var modifiedTimeString: String
        var rawModifiedTime = fileBean.modifiedTime

        if (isFolder) {
            modifiedTimeString = fileBean.modifiedTime.toLongOrNull()?.let {
                dateFormat.format(it * 1000)
            } ?: ""
        } else {
            sizeString = (fileBean.size.toLongOrNull() ?: 0L).formatFileSize() + " "
            modifiedTimeString = fileBean.modifiedTime

            if (fileBean.modifiedTime.isNotEmpty() && fileBean.modifiedTime.all { it.isDigit() }) {
                val parsedTime = runCatching {
                    dateFormat.parse(fileBean.modifiedTime)?.time?.div(1000)
                }.getOrNull()
                if (parsedTime != null) {
                    rawModifiedTime = parsedTime.toString()
                }
            }

            if (fileBean.currentPlayTime != 0 && fileBean.playLong != 0.00) {
                val playTime =
                    ((fileBean.currentPlayTime.toFloat() / fileBean.playLong) * 100).roundToInt()
                playLongRatio = "▶️ $playTime%"
            }
        }

        // 图标与时长处理
        var playLongString = fileBean.playLongString
        val ext =
            fileBean.icoString.ifEmpty { fileBean.name.substringAfterLast('.', "").lowercase() }
        val icoRes = when {
            isFolder -> R.drawable.folder
            fileBean.isVideo == 1 || ext in VIDEO_EXTS -> {
                playLongString = generateTime(fileBean.playLong.toLong()) + " "
                R.drawable.mp4
            }

            ext in HTML_EXTS -> R.drawable.web
            ext in ZIP_EXTS -> R.drawable.zip
            ext in IMG_EXTS -> R.drawable.png
            ext in TXT_EXTS -> R.drawable.txt
            ext in AUDIO_EXTS -> {
                playLongString = generateTime(fileBean.playLong.toLong()) + " "
                R.drawable.mp3
            }

            ext == "apk" -> R.drawable.apk
            ext == "iso" -> R.drawable.iso
            ext == "torrent" -> R.drawable.torrent
            else -> fileBean.fileIco
        }

        // 使用 copy() 拷贝并返回更新后的不可变对象
        fileBean.copy(
            fileId = finalFileId,
            parentId = finalParentId,
            isFolder = isFolder,
            fileIco = icoRes,
            updateTimeString = updateTimeString,
            createTimeString = createTimeString,
            modifiedTimeString = modifiedTimeString,
            modifiedTime = rawModifiedTime,
            sizeString = sizeString,
            playLongRatio = playLongRatio,
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
