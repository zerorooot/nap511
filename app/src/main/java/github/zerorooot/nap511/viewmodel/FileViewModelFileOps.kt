package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.RenameBean
import github.zerorooot.nap511.util.App
import kotlinx.coroutines.launch

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

    val fileBean = cutFileList[0]
    val cid = if (fileBean.isFolder) fileBean.parentId else fileBean.categoryId
    if (cid == currentCid) {
        App.instance.toast("禁止原地移动～")
        return
    }
    viewModelScope.launch(exceptionHandler) {
        val hashMapOf = hashMapOf<String, String>()
        hashMapOf["pid"] = currentCid
        cutFileList.forEachIndexed { index, fileBean ->
            hashMapOf["fid[$index]"] = fileBean.fileId
        }
        val move = fileService.move(hashMapOf)

        val message = if (move.state) {
            cutFileList.forEach { i -> i.isSelect = false }
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
        val createFolder = fileService.createFolder(cid, folderName)
        val message = if (createFolder.state) {
            refresh(cid)
            "创建文件夹 $folderName 成功"
        } else {
            "创建失败"
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
        val beforeImageBeanCache =
            imageBeanCache.getOrDefault(currentCid, hashMapOf())

        //提前删除，优化速度
        fileBeanList.remove(fileBean)
        fileListCache[currentCid]!!.fileBeanList.remove(fileBean)
        clickMap[currentCid] = clickMap.getOrDefault(currentCid, 0) - 1

        //删除文件夹内的文件夹
        if (fileBean.isFolder) {
            val results = mutableListOf<String>()
            val walk: (String) -> Unit = object : (String) -> Unit {
                override fun invoke(cid: String) {
                    fileListCache[cid]?.fileBeanList?.stream()?.filter { it.isFolder }
                        ?.forEach {
                            results.add(it.categoryId)
                            this(it.categoryId)
                        }
                }
            }
            walk(fileBean.categoryId)
            results.forEach { fileListCache.remove(it) }
        }

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

        val mapOf = hashMapOf<String, String>()
        mapOf["ignore_warn"] = "1"
        mapOf["pid"] = cid
        val filter = fileBeanList.filter { i -> i.isSelect }
        filter.forEachIndexed { index: Int, fileBean: FileBean ->
            mapOf["fid[$index]"] = fileBean.fileId
            //update image cache
            imageBeanCache[cid]?.remove(index)
        }
        //提前删除，优化速度
        fileBeanList.removeAll(filter)
        fileListCache[cid]!!.fileBeanList = ArrayList(fileBeanList)
        clickMap[cid] = clickMap.getOrDefault(cid, 0) - filter.size

        recoverFromLongPress()

        val deleteMultiple = fileService.deleteMultiple(mapOf)
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
