package github.zerorooot.nap511.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileInfo
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.ImageBean
import github.zerorooot.nap511.bean.LocationBean
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.OrderEnum
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.bean.RemainingSpaceBean
import github.zerorooot.nap511.bean.RenameBean
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.service.FileService
import github.zerorooot.nap511.service.Sha1Service
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.DialogSwitchUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

@SuppressLint("MutableCollectionMutableState")
class FileViewModel(private val cookie: String, private val application: Application) :
    ViewModel() {
    var fileBeanList = mutableStateListOf<FileBean>()
    var unzipBeanList = mutableStateOf(ZipBeanList())
    var remainingSpace by mutableStateOf(RemainingSpaceBean())
    var textBodyByteArray: ByteArray? = null

    var appBarTitle by mutableStateOf(application.resources.getString(R.string.app_name))

    private val _currentPath = MutableStateFlow("")
    var currentPath = _currentPath.asStateFlow()

    var currentCid: String by mutableStateOf("0")

    //当前cid下的文件数量
    private var count: Int by mutableIntStateOf(0)

    private var fileListCache = hashMapOf<String, FilesBean>()
    private var pathList = emptyList<PathBean>()

    private var cutFileList = emptyList<FileBean>()


    private val _isRefreshing = MutableStateFlow(false)
    var isRefreshing = _isRefreshing.asStateFlow()

    /**
     * 打开对话框相关
     */
    val dialogSwitchUtil = DialogSwitchUtil.getInstance()


    /**
     *所选中的文件/文件夹
     */
    var selectIndex by mutableIntStateOf(0)

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

    var fileInfo by mutableStateOf(FileInfo())

    //小文件缓存
    private var textFileCache = hashMapOf<FileBean, ByteArray?>()
    var orderBean = OrderBean(OrderEnum.name, 1)
    private val fileService: FileService by lazy {
        FileService.getInstance(cookie)
    }

    private val fileRepository: FileRepository by lazy {
        FileRepository.getInstance(cookie)
    }

    fun isFileScreenListState() = ::fileScreenListState.isInitialized

    fun init() {
        val saveRequestCache = DataStoreUtil.getData(ConfigKeyUtil.SAVE_REQUEST_CACHE, true)
        if (saveRequestCache && fileListCache.isEmpty()) {
            val file = App.cacheFile
            val content = if (file.exists()) file.readText() else "{}"
            val type = object : TypeToken<HashMap<String, FilesBean>?>() {}.type
            fileListCache = Gson().fromJson(content, type)
            XLog.d("loading file list cache ${fileListCache.size}")
        }
        getFiles(currentCid)
    }

    override fun onCleared() {
        super.onCleared()
        saveFileCache()
    }

    fun saveFileCache() {
        val saveRequestCache = DataStoreUtil.getData(ConfigKeyUtil.SAVE_REQUEST_CACHE, true)
        if (saveRequestCache) {
            val type = object : TypeToken<HashMap<String, FilesBean>?>() {}.type
            val json = Gson().toJson(fileListCache, type)
            val file = App.cacheFile
            file.writeText(json)
            XLog.d("save file list cache ${fileListCache.size}")
        }
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
            recoverFromLongPress()
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

    fun updateFileCache(cid: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            if (fileListCache.containsKey(cid)) {
                _isRefreshing.value = false
                return@launch
            }
            fileService.order(
                hashMapOf(
                    "user_order" to orderBean.type,
                    "user_asc" to orderBean.asc.toString(),
                    "file_id" to currentCid,
                    "fc_mix" to "0"
                )
            )
            val files = fileService.getFiles(cid = cid, order = orderBean.type, asc = orderBean.asc)
            setFileBeanProperty(files.fileBeanList)
//            setFiles(files)
            fileListCache[cid] = files
            _isRefreshing.value = false
        }
    }

    fun setRefreshingStatus(status: Boolean) {
        _isRefreshing.value = status
    }

    /**
     * 获取剩余空间
     */
    fun getRemainingSpace() {
        viewModelScope.launch {
            val gson = fileService.remainingSpace()
            if (!gson.get("state").asBoolean) {
                //{"state":false,"error":"登录超时，请重新登录。","errNo":990001,"request":"/files/index_info?count_space_nums=1"}
                return@launch
            }
            val spaceInfo = gson.getAsJsonObject("data").getAsJsonObject("space_info")
            val allUse = spaceInfo.getAsJsonObject("all_use").get("size").asLong
            val allUseString = spaceInfo.getAsJsonObject("all_use").get("size_format").asString
            val allTotal = spaceInfo.getAsJsonObject("all_total").get("size").asLong
            val allTotalString = spaceInfo.getAsJsonObject("all_total").get("size_format").asString
            val allRemain = spaceInfo.getAsJsonObject("all_remain").get("size").asLong
            val allRemainString =
                spaceInfo.getAsJsonObject("all_remain").get("size_format").asString
            remainingSpace = RemainingSpaceBean(
                allRemain, allRemainString, allTotal, allTotalString, allUse, allUseString
            )
        }
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
                App.instance.toast("获取文件列表失败，建议更新您的Cookie")
                App.cacheFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
                App.instance.toast("${e.message}，请重试～")
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
                fileBean.sizeString = fileRepository.formatFileSize(fileBean.size.toLong()) + " "
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
                "torrent" -> fileBean.fileIco = R.drawable.torrent
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
                refresh(currentCid)
            } else {
                App.instance.toast("排序失败")
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
            App.instance.toast("????????")
        }

    }

    fun selectToDown() {
        try {
            val indexOf = fileBeanList.indexOf(fileBeanList.filter { i -> i.isSelect }[0])
            for (i in indexOf until fileBeanList.size) {
                select(i)
            }
        } catch (_: Exception) {
            App.instance.toast("????????")
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
        recoverFromLongPress()
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
        //提前保存cid,防止进入其他文件夹后刷新当前目录
        val tempCid = currentCid
        isCutState = false
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

                refresh(tempCid)
                "移动${cutFileList.size}个文件成功"
            } else {
                "移动失败~"
            }
            App.instance.toast(message)
        }
    }


    fun createFolder(folderName: String) {
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

    fun refresh() {
        refresh(currentCid)
    }

    private fun refresh(cid: String) {
        recoverFromLongPress()
        fileListCache.remove(cid)
        // XLog.d("fileViewModel.refresh cid $cid, currentCid $currentCid")
        if (cid == currentCid) {
            getFiles(currentCid)
        } else {
            updateFileCache(cid)
        }
        //图片缓存
        imageBeanCache.remove(cid)
    }

    fun getFileInfo(index: Int) {
        viewModelScope.launch {
            val fileBean = fileBeanList[index]
            fileInfo = if (fileBean.isFolder) {
                fileRepository.getFileInfo(fileBean.categoryId)
            } else {
                fileRepository.getFileInfo(fileBean.fileId)
            }
            dialogSwitchUtil.isOpenFileInfoDialog = true
        }
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
            imageBeanCache[currentCid]?.removeIf { i -> i.fileName == fileBean.name }

            val fid = fileBean.fileId
            val pid = currentCid

            val delete = fileRepository.delete(pid, fid)

            val message = if (delete.state) {
                "删除 ${fileBean.name} 成功"
            } else {
                fileBeanList = beforeList
                fileListCache[currentCid] = beforeFileListCache!!
                clickMap[currentCid] = beforeClickMap
                imageBeanCache[currentCid] = beforeImageBeanCache!!
                "删除 ${fileBean.name} 失败~"
            }
            App.instance.toast(message)
        }
    }

    fun rename(name: String) {
        viewModelScope.launch {
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

    fun deleteMultiple() {
        viewModelScope.launch {
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
                imageBeanCache[cid]?.removeIf { i -> i.fileName == fileBean.name }
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

    /**
     * 从长按状态恢复
     */
    fun recoverFromLongPress() {
        isLongClickState = false
        appBarTitle = application.resources.getString(R.string.app_name)
    }

    fun search(searchKey: String) {
        viewModelScope.launch {
            isSearchState = true
            val files = fileService.search(currentCid, searchKey)
            setFileBeanProperty(files.fileBeanList)
            fileBeanList.clear()
            fileBeanList.addAll(files.fileBeanList)
            appBarTitle = "搜索-$searchKey"
        }
    }

    fun getZipListFile(
        fileName: String = "", paths: String = "文件", isCheck: Boolean = true
    ) {
        viewModelScope.launch {
            val fileBean = fileBeanList[selectIndex]
            if (isCheck) {
                //首次打开
                if (!dialogSwitchUtil.isOpenUnzipDialog && paths == "文件") {
                    if (fileRepository.isZipFileEncryption(fileBean.pickCode)) {
                        if (!fileRepository.tryToExtract(fileBean.pickCode)) {
                            XLog.d("${fileBean.name} is encryption zip file")
                            dialogSwitchUtil.isOpenUnzipPasswordDialog = true
                            return@launch
                        }
                    }
                }
            }
            unzipBeanList.value = fileRepository.getZipListFile(fileBean.pickCode, fileName, paths)
            dialogSwitchUtil.isOpenUnzipDialog = true
        }
    }

    fun unzipFile(files: List<String>?, dirs: List<String>?) {
        viewModelScope.launch {
            val refreshCid = currentCid
            val fileBean = fileBeanList[selectIndex]
            val pickCode = fileBean.pickCode
            val unzipFolderName = fileBean.name.substring(0, fileBean.name.length - 4)

            //确定当前目录下是否存在同名文件，如果不存在，则新建一个
            val currentUnzipFolderNameList =
                fileBeanList.filter { i -> i.isFolder && i.name == unzipFolderName }
            var zipFileCid = currentCid
            if (currentUnzipFolderNameList.isEmpty()) {
                val createFolderMessage = fileRepository.createFolder(
                    currentCid, unzipFolderName
                )
                XLog.d("fileViewModel.unzipFile $createFolderMessage")
                zipFileCid = createFolderMessage.cid
            }

            val unzipFile = fileRepository.unzipFile(pickCode, zipFileCid, files, dirs)

            if (unzipFile.first) {
                refresh(refreshCid)
            } else {
                App.instance.toast(unzipFile.second)
            }
        }
    }

    fun decryptZip(secret: String) {
        viewModelScope.launch {
            val fileBean = fileBeanList[selectIndex]
            val pickCode = fileBean.pickCode
            dialogSwitchUtil.isOpenUnzipPasswordDialog = false
            val decryptZip = fileRepository.decryptZip(pickCode, secret)
            if (!decryptZip) {
                App.instance.toast("密码错误～")
                return@launch
            }

            //{"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":4,"progress":100}}}
            if (fileRepository.tryToExtract(pickCode)) {
                getZipListFile(isCheck = false)
            }else{
                App.instance.toast("服务器解压中～")
            }
        }
    }


    fun downloadText(fileBean: FileBean) {
        thread {
            var bytes = textFileCache[fileBean]
            if (bytes == null) {
                bytes = fileRepository.getDownloadInputStream(fileBean.pickCode, fileBean.fileId).readBytes()
                textFileCache.put(fileBean, bytes)
            }
            textBodyByteArray = bytes
            dialogSwitchUtil.isOpenTextBodyDialog = true
            setRefreshingStatus(false)
        }
    }


    fun startSendAria2Service(index: Int) {
        val fileBean = fileBeanList[index]
        if (fileBean.isFolder) {
            App.instance.toast("暂时无法下载文件夹")
            return
        }
        val intent = Intent(application, Sha1Service::class.java)
        intent.putExtra(ConfigKeyUtil.COMMAND, ConfigKeyUtil.SENT_TO_ARIA2)
        intent.putExtra("list", Gson().toJson(fileBean))
        intent.putExtra("cookie", cookie)
        application.startService(intent)
    }


//    fun selectAll() {
//        val a = arrayListOf<FileBean>()
//        fileBeanList.forEach { i ->
//            i.isSelect = true
//            a.add(i)
//        }
//        fileBeanList.clear()
//        fileBeanList.addAll(a)
//        appBarTitle = fileBeanList.size.toString()
//    }

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

    fun unSelect() {
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

    @SuppressLint("DefaultLocale")
    private fun generateTime(totalSeconds: Long): String {
        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60 % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) String.format(
            "%02d:%02d:%02d", hours, minutes, seconds
        ) else String.format("%02d:%02d", minutes, seconds)
    }
}
