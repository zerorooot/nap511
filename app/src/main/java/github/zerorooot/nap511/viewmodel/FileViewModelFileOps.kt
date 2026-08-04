package github.zerorooot.nap511.viewmodel

import android.annotation.SuppressLint
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
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
    // 提前保存cid,防止进入其他文件夹后刷新当前目录
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
            cutFileList.forEach { i -> i.isSelect = false }

            // 1. 获取源目录 cid 的缓存，剔除已移走的文件并写回缓存
            val sourceFilesBean = fileCacheManager.get(cid, readDisk = saveRequestCache)
            if (sourceFilesBean != null) {
                sourceFilesBean.fileBeanList.removeAll(cutFileList.toSet())
                fileCacheManager.put(cid, sourceFilesBean, saveToDisk = saveRequestCache)
            }

            // 2. 移除被剪切文件夹本身的缓存，防止路径等元数据未刷新
            cutFileList.forEach { i ->
                if (i.isFolder) {
                    fileCacheManager.remove(i.categoryId)
                }
            }

            // 3. 刷新目标目录（内部已适配清除目标 cid 缓存并重新读取）
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
        val cid = currentCid
        val beforeList = ArrayList(fileBeanList)

        // 备份当前目录缓存（浅拷贝 list 避免引用被修改导致无法回滚）
        val beforeFileListCache = fileCacheManager.get(cid, readDisk = saveRequestCache)?.let {
            it.copy(fileBeanList = ArrayList(it.fileBeanList))
        }
        val beforeClickMap = clickMap.getOrDefault(cid, 0)
        val beforeImageBeanCache = imageBeanCache.getOrDefault(cid, hashMapOf())

        // 1. 乐观更新：优先移除 UI 与当前 CID 缓存
        fileBeanList.remove(fileBean)

        val currentCache = fileCacheManager.get(cid, readDisk = saveRequestCache)
        if (currentCache != null) {
            currentCache.fileBeanList.remove(fileBean)
            fileCacheManager.put(cid, currentCache, saveToDisk = saveRequestCache)
        }

        clickMap[cid] = clickMap.getOrDefault(cid, 0) - 1

        // 2. 如果是文件夹，递归递归清除其子文件夹在 CacheManager 中的缓存
        if (fileBean.isFolder) {
            val removeFolderCids = mutableListOf<String>()

            suspend fun walk(targetCid: String) {
                val cached = fileCacheManager.get(targetCid, readDisk = saveRequestCache)
                cached?.fileBeanList?.filter { it.isFolder }?.forEach { subFolder ->
                    removeFolderCids.add(subFolder.categoryId)
                    walk(subFolder.categoryId)
                }
            }

            walk(fileBean.categoryId)
            removeFolderCids.add(fileBean.categoryId)

            // 批量移除缓存
            removeFolderCids.forEach { folderCid ->
                fileCacheManager.remove(folderCid)
            }
        }

        imageBeanCache[cid]?.remove(index)

        // 3. 发起网络请求
        val fid = fileBean.fileId
        val pid = cid
        val delete = fileRepository.delete(pid, fid)

        val message = if (delete.state) {
            "删除 ${fileBean.name} 成功"
        } else {
            // 4. 失败回滚
            fileBeanList.clear()
            fileBeanList.addAll(beforeList)

            if (beforeFileListCache != null) {
                fileCacheManager.put(cid, beforeFileListCache, saveToDisk = saveRequestCache)
            }
            clickMap[cid] = beforeClickMap
            imageBeanCache[cid] = beforeImageBeanCache
            "删除 ${fileBean.name} 失败~${delete.errorMsg}"
        }
        App.instance.toast(message)
    }
}

internal fun FileViewModel.rename(name: String) {
    viewModelScope.launch(exceptionHandler) {
        val cid = currentCid
        val fileBean = fileBeanList[selectIndex]
        val beforeList = ArrayList(fileBeanList)

        // 备份当前目录缓存
        val beforeFileListCache = fileCacheManager.get(cid, readDisk = saveRequestCache)?.let {
            it.copy(fileBeanList = ArrayList(it.fileBeanList))
        }

        // 1. 乐观更新 UI State 与 当前 Cid 缓存
        val updatedFileBean = fileBean.copy(name = name)
        fileBeanList[selectIndex] = updatedFileBean

        val currentCache = fileCacheManager.get(cid, readDisk = saveRequestCache)
        if (currentCache != null && selectIndex in currentCache.fileBeanList.indices) {
            currentCache.fileBeanList[selectIndex] = updatedFileBean
            fileCacheManager.put(cid, currentCache, saveToDisk = saveRequestCache)
        }

        // 2. 如果重命名的是文件夹，更新该文件夹自身缓存中的 path 路径名称
        if (fileBean.isFolder) {
            val targetFolderCache = fileCacheManager.get(fileBean.categoryId, readDisk = saveRequestCache)
            if (targetFolderCache != null && targetFolderCache.path.isNotEmpty()) {
                targetFolderCache.path.last().name = name
                fileCacheManager.put(fileBean.categoryId, targetFolderCache, saveToDisk = saveRequestCache)
            }
        }

        // 3. 请求网络
        val rename = fileRepository.rename(RenameBean(fileBean.fileId, name).toRequestBody())
        val message = if (rename.state) {
            "重命名成功"
        } else {
            // 4. 失败回滚
            fileBeanList.clear()
            fileBeanList.addAll(beforeList)

            if (beforeFileListCache != null) {
                fileCacheManager.put(cid, beforeFileListCache, saveToDisk = saveRequestCache)
            }
            "重命名失败"
        }
        App.instance.toast(message)
    }
}

internal fun FileViewModel.deleteMultiple() {
    viewModelScope.launch(exceptionHandler) {
        val cid = currentCid
        val beforeList = ArrayList(fileBeanList)

        // 备份当前目录缓存
        val beforeFileListCache = fileCacheManager.get(cid, readDisk = saveRequestCache)?.let {
            it.copy(fileBeanList = ArrayList(it.fileBeanList))
        }
        val beforeClickMap = clickMap.getOrDefault(cid, 0)

        val mapOf = hashMapOf<String, String>()
        mapOf["ignore_warn"] = "1"
        mapOf["pid"] = cid
        val filter = fileBeanList.filter { i -> i.isSelect }

        // 1. 递归收集并清理被选中文件夹的子文件夹缓存
        filter.forEachIndexed { index: Int, fileBean: FileBean ->
            mapOf["fid[$index]"] = fileBean.fileId
            imageBeanCache[cid]?.remove(index)

            if (fileBean.isFolder) {
                val removeFolderCids = mutableListOf<String>()

                suspend fun walk(targetCid: String) {
                    val cached = fileCacheManager.get(targetCid, readDisk = saveRequestCache)
                    cached?.fileBeanList?.filter { it.isFolder }?.forEach { subFolder ->
                        removeFolderCids.add(subFolder.categoryId)
                        walk(subFolder.categoryId)
                    }
                }

                walk(fileBean.categoryId)
                removeFolderCids.add(fileBean.categoryId)

                removeFolderCids.forEach { folderCid ->
                    fileCacheManager.remove(folderCid)
                }
            }
        }

        // 2. 乐观更新：同步更新 UI 列表与当前 Cid 的缓存
        fileBeanList.removeAll(filter.toSet())

        val currentCache = fileCacheManager.get(cid, readDisk = saveRequestCache)
        if (currentCache != null) {
            currentCache.fileBeanList = ArrayList(fileBeanList)
            fileCacheManager.put(cid, currentCache, saveToDisk = saveRequestCache)
        }

        clickMap[cid] = clickMap.getOrDefault(cid, 0) - filter.size

        recoverFromLongPress()

        // 3. 请求网络
        val deleteMultiple = fileRepository.deleteMultiple(mapOf)
        val message = if (deleteMultiple.state) {
            "成功删除 ${filter.size} 个文件"
        } else {
            // 4. 失败回滚
            fileBeanList.clear()
            fileBeanList.addAll(beforeList)

            if (beforeFileListCache != null) {
                fileCacheManager.put(cid, beforeFileListCache, saveToDisk = saveRequestCache)
            }
            clickMap[cid] = beforeClickMap
            "删除 ${filter.size} 个文件失败~"
        }
        App.instance.toast(message)
    }
}

internal fun FileViewModel.setFileBeanProperty(fileBeanList: ArrayList<FileBean>) {
    fileBeanList.forEach { fileBean ->
        fileBean.updateTimeString =
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                fileBean.updateTime.toLong() * 1000
            )
        fileBean.createTimeString =
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                fileBean.createTime.toLong() * 1000
            )
        if (fileBean.fileId == "") {
            fileBean.fileId = fileBean.categoryId
            fileBean.fileIco = R.drawable.folder
            fileBean.isFolder = true
            fileBean.modifiedTimeString =
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                    fileBean.modifiedTime.toLong() * 1000
                )
        } else {
            fileBean.sizeString = fileRepository.formatFileSize(fileBean.size.toLong()) + " "
            fileBean.modifiedTimeString = fileBean.modifiedTime
            if (fileBean.modifiedTime.isDigitsOnly()) {
                fileBean.modifiedTime =
                    (SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(
                        fileBean.modifiedTime
                    )!!.time / 1000).toString()
            }
            if (fileBean.currentPlayTime != 0 && fileBean.playLong != 0.00) {
                val playTime =
                    ((fileBean.currentPlayTime.toFloat() / fileBean.playLong) * 100).roundToInt()
                fileBean.createTimeString = "▶️ $playTime% ${fileBean.createTimeString}"
            }
        }
        if (fileBean.isVideo == 1) {
            fileBean.fileIco = R.drawable.mp4
            fileBean.playLongString = generateTime(fileBean.playLong.toLong()) + " "
        }
        if (fileBean.icoString == "mp3" || fileBean.icoString == "m4a") {
            fileBean.fileIco = R.drawable.mp3
            fileBean.playLongString = generateTime(fileBean.playLong.toLong()) + " "
        }
        when (fileBean.icoString) {
            "apk" -> fileBean.fileIco = R.drawable.apk
            "iso" -> fileBean.fileIco = R.drawable.iso
            "zip" -> fileBean.fileIco = R.drawable.zip
            "7z" -> fileBean.fileIco = R.drawable.zip
            "rar" -> fileBean.fileIco = R.drawable.zip
            "png" -> fileBean.fileIco = R.drawable.png
            "jpg" -> fileBean.fileIco = R.drawable.png
            "txt" -> fileBean.fileIco = R.drawable.txt
            "torrent" -> fileBean.fileIco = R.drawable.torrent
        }
    }
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