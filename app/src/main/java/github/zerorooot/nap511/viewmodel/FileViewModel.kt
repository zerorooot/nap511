package github.zerorooot.nap511.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.*
import github.zerorooot.nap511.service.FileService
import github.zerorooot.nap511.service.Sha1Service
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.NullPointerException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@SuppressLint("MutableCollectionMutableState")
class FileViewModel(private val cookie: String, private val application: Application) :
    ViewModel() {
    var fileBeanList = mutableStateListOf<FileBean>()
//    var selectedItem by mutableStateOf("我的文件")

    var appBarTitle by mutableStateOf(application.resources.getString(R.string.app_name))

    private val _currentPath = MutableStateFlow("")
    var currentPath = _currentPath.asStateFlow()

    var currentCid: String by mutableStateOf("0")
    private var count: Int by mutableIntStateOf(0)

    private var fileListCache = hashMapOf<String, FilesBean>()
    private var pathList = emptyList<PathBean>()

    private var cutFileList = emptyList<FileBean>()


    private val _isRefreshing = MutableStateFlow(false)
    var isRefreshing = _isRefreshing.asStateFlow()

    //打开对话框相关
    var isOpenCreateFolderDialog by mutableStateOf(false)
    var isOpenRenameFileDialog by mutableStateOf(false)
    var isOpenFileInfoDialog by mutableStateOf(false)
    var isOpenFileOrderDialog by mutableStateOf(false)
    var isOpenAria2Dialog by mutableStateOf(false)
    var selectIndex by mutableIntStateOf(0)

    var isSearch by mutableStateOf(false)


    //图片浏览相关
    var photoFileBeanList = mutableListOf<FileBean>()
    var photoIndexOf by mutableIntStateOf(-1)
    var imageBeanList = mutableStateListOf<ImageBean>()
    private val imageBeanCache = hashMapOf<String, SnapshotStateList<ImageBean>>()

    //位置与点击记录相关
    val clickMap = mutableStateMapOf<String, Int>()
    private var currentLocation = hashMapOf<String, LocationBean>()
    lateinit var fileScreenListState: LazyListState

    //相关状态
    var isLongClickState: Boolean by mutableStateOf(false)
    var isCutState: Boolean by mutableStateOf(false)
    var isSearchState: Boolean by mutableStateOf(false)

    var orderBean = OrderBean(OrderEnum.name, 1)
    private val fileService: FileService by lazy {
        FileService.getInstance(cookie)
    }

    fun isFileScreenListState() = ::fileScreenListState.isInitialized

    fun init() {
        getFiles(currentCid)
    }

    fun back() {
        if (isSearchState) {
            fileBeanList.clear()
            setFiles(fileListCache[currentCid]!!)
            appBarTitle = application.resources.getString(R.string.app_name)
            isSearchState = false
            return
        }

        if (isLongClickState) {
            isLongClickState = false
            appBarTitle = application.resources.getString(R.string.app_name)
            fileBeanList.map { i -> i.isSelect = false }
            return
        }

        if (currentCid != "0") {
            getFiles(pathList[pathList.size - 2].cid)
            return
        }

        if (isCutState) {
            isCutState = false
            return
        }
    }

    fun setListLocation(path: String) {
        val locationBean = LocationBean(
            fileScreenListState.firstVisibleItemIndex,
            fileScreenListState.firstVisibleItemScrollOffset
        )
        currentLocation[path] = locationBean
    }

    fun setListLocationAndClickCache(index: Int) {
        val currentPath = _currentPath.value
        //记录上级目录当前的位置
        setListLocation(currentPath)
        //标记此点击文件，方便确认到底点了那个
        clickMap[currentPath] = index
    }

    fun getListLocation(path: String) {
        App.scope.launch {
            val locationBean = currentLocation[path] ?: run {
                LocationBean(0, 0)
            }
//            Thread.sleep(100)
            fileScreenListState.scrollToItem(
                locationBean.firstVisibleItemIndex, locationBean.firstVisibleItemScrollOffset
            )
        }
    }

    fun getImage(fileBeanList: List<FileBean>, indexOf: Int) {
        if (imageBeanCache.containsKey(currentCid)) {
            imageBeanList = imageBeanCache[currentCid]!!
            return
        }
        imageBeanList.clear()
        fileBeanList.forEach { fileBean ->
            imageBeanList.add(
                ImageBean(
                    fileName = fileBean.name, pickCode = fileBean.pickCode, fileSha1 = fileBean.sha1
                )
            )
        }
        //获取当前点击的
        viewModelScope.launch {
            imageBeanList[indexOf] = fileService.image(
                fileBeanList[indexOf].pickCode, System.currentTimeMillis() / 1000
            ).imageBean
        }
        //加载其他剩余的
        viewModelScope.launch {
            try {
                fileBeanList.forEachIndexed { index, fileBean ->
                    if (indexOf != index) {
                        imageBeanList[index] = fileService.image(
                            fileBean.pickCode, System.currentTimeMillis() / 1000
                        ).imageBean
                    }
                }
            } catch (_: Exception) {

            }
        }

        imageBeanCache[currentCid] = imageBeanList
    }

    fun getFiles(cid: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            if (fileListCache.containsKey(cid)) {
                setFiles(fileListCache[cid]!!)
                _isRefreshing.value = false
                return@launch
            }

            try {
                fileService.order(
                    hashMapOf(
                        "user_order" to orderBean.type,
                        "user_asc" to orderBean.asc.toString(),
                        "file_id" to currentCid,
                        "fc_mix" to "0"
                    )
                )
                val files =
                    fileService.getFiles(cid = cid, order = orderBean.type, asc = orderBean.asc)
                setFileBeanProperty(files.fileBeanList)
                setFiles(files)
                _isRefreshing.value = false
            } catch (e: NullPointerException) {
                Toast.makeText(
                    application,
                    "获取文件列表失败，建议更新您的Cookie",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    application,
                    "${e.message}，请重试～",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun setFileBeanProperty(fileBeanList: ArrayList<FileBean>) {
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
                fileBean.sizeString = android.text.format.Formatter.formatFileSize(
                    application, fileBean.size.toLong()
                ) + " "
                fileBean.modifiedTimeString = fileBean.modifiedTime
                if (fileBean.modifiedTime.isDigitsOnly()) {
                    fileBean.modifiedTime =
                        (SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(
                            fileBean.modifiedTime
                        )!!.time / 1000).toString()
                }
                if (fileBean.currentPlayTime != 0) {
                    val playTime =
                        ((fileBean.currentPlayTime.toFloat() / fileBean.playLong) * 100).roundToInt()
                    fileBean.createTimeString = "▶️ $playTime% ${fileBean.createTimeString}"
                }
            }
            if (fileBean.isVideo == 1) {
                fileBean.fileIco = R.drawable.mp4
                //设置视频时间
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
                "mp3" -> fileBean.fileIco = R.drawable.mp3
                "txt" -> fileBean.fileIco = R.drawable.txt
            }
        }
    }

    fun order() {
        /**
         *
        user_order:file_size
        file_id:2573609193685653011
        user_asc:1
        fc_mix:0
         */
        val map = hashMapOf(
            "user_order" to orderBean.type,
            "user_asc" to orderBean.asc.toString(),
            "file_id" to currentCid,
            "fc_mix" to "0"
        )
        viewModelScope.launch {
            val order = fileService.order(map)
            if (order.state) {
                refresh()
            } else {
                Toast.makeText(application, "排序失败", Toast.LENGTH_SHORT).show()
            }

        }
    }

    fun selectToUp() {
        try {
            val indexOf = fileBeanList.indexOf(fileBeanList.filter { i -> i.isSelect }[0])
            for (i in 0..indexOf) {
                select(i)
            }
        } catch (_: Exception) {
            Toast.makeText(application, "????????", Toast.LENGTH_SHORT).show()
        }

    }

    fun selectToDown() {
        try {
            val indexOf = fileBeanList.indexOf(fileBeanList.filter { i -> i.isSelect }[0])
            for (i in indexOf until fileBeanList.size) {
                select(i)
            }
        } catch (_: Exception) {
            Toast.makeText(application, "????????", Toast.LENGTH_SHORT).show()
        }

    }

    fun cut(index: Int = -1) {
        cutFileList = if (index == -1) {
            fileBeanList.filter { i -> i.isSelect }
        } else {
            select(index)
            arrayListOf(fileBeanList[index])
        }
        isCutState = true
        isLongClickState = false
        appBarTitle = application.resources.getString(R.string.app_name)
        unSelect()
    }

    fun cancelCut() {
        unSelect()
        isCutState = false
        cutFileList = emptyList()
    }

    fun removeFile() {
        if (cutFileList.isEmpty()) {
            isCutState = false
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
                fileListCache[cid]?.fileBeanList?.removeAll(cutFileList.toSet())
                //移除被剪切文件夹的缓存，防止路径未更改
                cutFileList.forEach { i ->
                    if (i.isFolder) {
                        fileListCache.remove(i.categoryId)
                    }
                }

                refresh()
                "移动${cutFileList.size}个文件成功"
            } else {
                "移动失败~"
            }
            isCutState = false
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
        isLongClickState = false
        appBarTitle = application.resources.getString(R.string.app_name)
        //图片缓存
        imageBeanCache.remove(currentCid)
    }

    fun delete(index: Int) {
        viewModelScope.launch {
            val fileBean = fileBeanList[index]

            val beforeList = fileBeanList
            val beforeFileListCache = fileListCache[currentCid]
            val beforeClickMap = clickMap.getOrDefault(currentCid, 0)
            val beforeImageBeanCache = imageBeanCache[currentCid]

            //提前删除，优化速度
            fileBeanList.remove(fileBean)
            fileListCache[currentCid]!!.fileBeanList.remove(fileBean)
            clickMap[currentCid] = clickMap.getOrDefault(currentCid, 0) - 1
            //delete image bean
            imageBeanCache[currentCid]?.removeIf { i -> i.fileName == fileBean.name }

            val fid = fileBean.fileId
            val pid = currentCid

            val delete = fileService.delete(pid, fid)

            val message = if (delete.state) {
                "删除 ${fileBean.name} 成功"
            } else {
                fileBeanList = beforeList
                fileListCache[currentCid] = beforeFileListCache!!
                clickMap[currentCid] = beforeClickMap
                imageBeanCache[currentCid] = beforeImageBeanCache!!
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
                fileListCache[currentCid]!!.fileBeanList[selectIndex] = fileBean.copy(name = name)
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
            val beforeList = fileBeanList
            val beforeFileListCache = fileListCache[currentCid]
            val beforeClickMap = clickMap.getOrDefault(currentCid, 0)

            val mapOf = hashMapOf<String, String>()
            mapOf["ignore_warn"] = "1"
            mapOf["pid"] = currentCid
            val filter = fileBeanList.filter { i -> i.isSelect }
            filter.forEachIndexed { index: Int, fileBean: FileBean ->
                mapOf["fid[$index]"] = fileBean.fileId
                //update image cache
                imageBeanCache[currentCid]?.removeIf { i -> i.fileName == fileBean.name }
            }
            //提前删除，优化速度
            fileBeanList.removeAll(filter)
            fileListCache[currentCid]!!.fileBeanList = ArrayList(fileBeanList)
            clickMap[currentCid] = clickMap.getOrDefault(currentCid, 0) - filter.size

            isLongClickState = false
            appBarTitle = application.resources.getString(R.string.app_name)

            val deleteMultiple = fileService.deleteMultiple(mapOf)
            val message = if (deleteMultiple.state) {
                "成功删除 ${filter.size} 个文件"
            } else {
                fileBeanList = beforeList
                fileListCache[currentCid] = beforeFileListCache!!
                clickMap[currentCid] = beforeClickMap
                "删除 ${filter.size} 个文件失败~"
            }
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()

        }
    }

    fun search(searchKey: String) {
        viewModelScope.launch {
            isSearchState = true
            val files = fileService.search(currentCid, searchKey)
            setFileBeanProperty(files.fileBeanList)
            fileBeanList.clear()
            fileBeanList.addAll(files.fileBeanList)
            appBarTitle = "搜索"
        }
    }


    fun startSendAria2Service(index: Int) {
        val fileBean = fileBeanList[index]
        if (fileBean.isFolder) {
            Toast.makeText(application, "暂时无法下载文件夹", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(application, Sha1Service::class.java)
        intent.putExtra(ConfigUtil.command, ConfigUtil.sentToAria2)
        intent.putExtra("list", Gson().toJson(fileBean))
        intent.putExtra("cookie", cookie)
        application.startService(intent)
    }


    fun selectAll() {
        val a = arrayListOf<FileBean>()
        fileBeanList.forEach { i ->
            i.isSelect = true
            a.add(i)
        }
        fileBeanList.clear()
        fileBeanList.addAll(a)
        appBarTitle = fileBeanList.size.toString()
    }

    fun selectReverse() {
        val a = arrayListOf<FileBean>()
        fileBeanList.forEach { i ->
            i.isSelect = !i.isSelect
            a.add(i)
        }
        fileBeanList.clear()
        fileBeanList.addAll(a)
        appBarTitle = fileBeanList.filter { i -> i.isSelect }.size.toString()
    }

    fun select(index: Int) {
        val fb = fileBeanList[index]
        fileBeanList[index] = fb.copy(isSelect = !fb.isSelect)
        appBarTitle = fileBeanList.filter { i -> i.isSelect }.size.toString()
    }

    private fun unSelect() {
        val count = arrayListOf<Int>()
        fileBeanList.forEachIndexed { index, fileBean ->
            if (fileBean.isSelect) {
                count.add(index)
            }
        }

        count.forEach { i ->
            val fileBean = fileBeanList[i]
            fileBeanList[i] = fileBean.copy(isSelect = false)
        }
    }

    private fun setFiles(files: FilesBean) {
        fileBeanList.clear()
        fileBeanList.addAll(files.fileBeanList)

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

    private fun generateTime(totalSeconds: Long): String {
        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60 % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) String.format(
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        ) else String.format("%02d:%02d", minutes, seconds)
    }
}