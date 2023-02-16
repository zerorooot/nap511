package github.zerorooot.nap511.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.bean.*
import github.zerorooot.nap511.service.FileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import github.zerorooot.nap511.R

class FileViewModel(private val cookie: String, private val application: Application) :
    ViewModel() {
    var fileBeanList = mutableStateListOf<FileBean>()

    private val _currentPath = MutableStateFlow("")
    var currentPath = _currentPath.asStateFlow()

    private var currentCid: String by mutableStateOf("0")
    private var count: Int by mutableStateOf(0)

    private var fileListCache = hashMapOf<String, FilesBean>()
    private var pathList = emptyList<PathBean>()

    private var cutFileList = emptyList<FileBean>()

    var currentLocation = hashMapOf<String, LocationBean>()

    private val _isRefreshing = MutableStateFlow(false)
    var isRefreshing = _isRefreshing.asStateFlow()


    var isOpenCreateFolder by mutableStateOf(false)
    var isRenameFile by mutableStateOf(false)
    var isFileInfo by mutableStateOf(false)
    var selectIndex by mutableStateOf(0)
    var isLongClick: Boolean by mutableStateOf(false)
    var isCut: Boolean by mutableStateOf(false)

    private val fileService: FileService by lazy {
        FileService.getInstance(cookie)
    }


    fun init() {
        getFiles(currentCid)
    }

    fun back() {
        if (isLongClick) {
            isLongClick = false
            fileBeanList.map { i -> i.isSelect = false }
            return
        }

        if (currentCid != "0") {
            getFiles(pathList[pathList.size - 2].cid)
            return
        }

        if (isCut) {
            isCut = false
            return
        }
    }

    fun getFiles(cid: String) {
        _isRefreshing.value = true
        if (fileListCache.containsKey(cid)) {
            setFiles(fileListCache[cid]!!)
            _isRefreshing.value = false
            return
        }

        viewModelScope.launch {
            try {
                val files = fileService.getFiles(cid = cid)
                val fileBean = files.fileBean
                fileBean.forEach { fileBean ->
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
                        fileBean.sizeString = android.text.format.Formatter.formatFileSize(
                            application,
                            fileBean.size.toLong()
                        ) + " "
                        fileBean.modifiedTimeString = fileBean.modifiedTime
                        fileBean.modifiedTime =
                            (SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(
                                fileBean.modifiedTime
                            )!!.time / 1000).toString()
                        if (fileBean.currentPlayTime != 0) {
                            val playTime =
                                ((fileBean.currentPlayTime.toFloat() / fileBean.playLong) * 100).toString()
                                    .subSequence(0, 2).toString()
                            fileBean.createTimeString = "$playTime% ${fileBean.createTimeString}"
                        }
                    }
                    if (fileBean.isVideo == 1) {
                        fileBean.fileIco = R.drawable.mp4
                    }
                    when (fileBean.icoString) {
                        "apk"->fileBean.fileIco = R.drawable.apk
                        "iso"->fileBean.fileIco = R.drawable.iso
                        "zip" -> fileBean.fileIco = R.drawable.zip
                        "7z" -> fileBean.fileIco = R.drawable.zip
                        "rar" -> fileBean.fileIco = R.drawable.zip
                        "png" -> fileBean.fileIco = R.drawable.png
                        "jpg" -> fileBean.fileIco = R.drawable.png
                        "mp3" -> fileBean.fileIco = R.drawable.mp3
                        "txt" -> fileBean.fileIco = R.drawable.txt
                    }
                }
                setFiles(files)

                _isRefreshing.value = false
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(application, "获取文件列表失败~", Toast.LENGTH_SHORT).show()
                _isRefreshing.value = false
            }
        }
    }

    fun cut(index: Int = -1) {
        cutFileList = if (index == -1) {
            fileBeanList.filter { i -> i.isSelect }
        } else {
            select(index)
            arrayListOf(fileBeanList[index])
        }
        isCut = true
        isLongClick = false
    }

    fun removeFile() {
        if (cutFileList.isEmpty()) {
            isCut = false
            return
        }

        viewModelScope.launch {
            val hashMapOf = hashMapOf<String, String>()
            hashMapOf["pid"] = currentCid
            cutFileList.forEachIndexed { index, fileBean ->
                hashMapOf["fid[$index]"] = fileBean.fileId
            }
            val move = fileService.move(hashMapOf)

            val message = if (move.state) {
                cutFileList.forEach { i -> i.isSelect = false }
                val fileBean = cutFileList[0]
                val cid = if (fileBean.isFolder) fileBean.parentId else fileBean.categoryId
                //移除之前目录下剪切的文件
                fileListCache[cid]!!.fileBean.removeAll(cutFileList)
                //移除被剪切文件夹的缓存，防止路径未更改
                cutFileList.forEach {i->
                    if (i.isFolder) {
                        fileListCache.remove(i.categoryId)
                    }
                }

                refresh()
                "移动${cutFileList.size}个文件成功"
            } else {
                "移动失败~"
            }
            isCut = false
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
        }
    }


    fun createFolder(folderName: String) {
        viewModelScope.launch {
            val createFolder = fileService.createFolder(currentCid, folderName)
            val message = if (createFolder.state) {
                refresh()
                "创建文件夹 $folderName 成功"
            } else {
                "创建失败"
            }
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()

        }

    }

    fun refresh() {
        fileListCache.remove(currentCid)
        getFiles(currentCid)
    }

    fun delete(index: Int) {
        val fileBean = fileBeanList[index]
        viewModelScope.launch {
            val fid = fileBean.fileId
            val pid = currentCid

            val delete = fileService.delete(pid, fid)

            val message = if (delete.state) {
                fileBeanList.remove(fileBean)

                fileListCache[currentCid]!!.fileBean.remove(fileBean)

                "删除 ${fileBean.name} 成功"
            } else {
                "删除 ${fileBean.name} 失败~"
            }
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun rename(name: String) {
        val fileBean = fileBeanList[selectIndex]
        viewModelScope.launch {
            val rename = fileService.rename(RenameBean(fileBean.fileId, name).toRequestBody())
            val message = if (rename.state) {
                fileBeanList[selectIndex] = fileBean.copy(name = name)
                fileListCache[currentCid]!!.fileBean[selectIndex] = fileBean.copy(name = name)
                fileListCache[fileBean.categoryId]?.let { it.path.last().name = name }
                "重命名成功"
            } else {
                "重命名失败"
            }
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
        }

    }

    fun deleteMultiple() {
        viewModelScope.launch {
            val mapOf = hashMapOf<String, String>()
            mapOf["ignore_warn"] = "1"
            mapOf["pid"] = currentCid
            val filter = fileBeanList.filter { i -> i.isSelect }
            filter.forEachIndexed { index: Int, fileBean: FileBean ->
                mapOf["fid[$index]"] = fileBean.fileId
            }
            val deleteMultiple = fileService.deleteMultiple(mapOf)
            val message = if (deleteMultiple.state) {
                fileBeanList.removeAll(filter)
                fileListCache[currentCid]!!.fileBean = ArrayList(fileBeanList)

                "成功删除 ${filter.size} 个文件"
            } else {
                "删除 ${filter.size} 个文件失败~"
            }
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
            isLongClick = false
        }
    }


    fun selectAll() {
        val a = arrayListOf<FileBean>()
        fileBeanList.forEach { i ->
            i.isSelect = true
            a.add(i)
        }
        fileBeanList.clear()
        fileBeanList.addAll(a)
    }

    fun selectReverse() {
        val a = arrayListOf<FileBean>()
        fileBeanList.forEach { i ->
            i.isSelect = !i.isSelect
            a.add(i)
        }
        fileBeanList.clear()
        fileBeanList.addAll(a)
    }

    fun select(index: Int) {
        val fb = fileBeanList[index]
        fileBeanList[index] = fb.copy(isSelect = !fb.isSelect)
    }

    private fun setFiles(files: FilesBean) {
        fileBeanList.clear()
        fileBeanList.addAll(files.fileBean)
        currentCid = files.cid
        count = files.count
        pathList = files.path

        var path = ""
        pathList.forEach { i ->
            path = "$path/${i.name}"
        }
        _currentPath.value = path
        if (!fileListCache.containsKey(currentCid)) {
            fileListCache[currentCid] = files
        }
    }
}