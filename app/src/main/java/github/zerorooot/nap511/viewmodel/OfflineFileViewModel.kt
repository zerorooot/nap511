package github.zerorooot.nap511.viewmodel


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.OfflineInfo
import github.zerorooot.nap511.bean.OfflineTask
import github.zerorooot.nap511.bean.QuotaBean
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.repository.DialogEvent
import github.zerorooot.nap511.repository.DialogEventRepository
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class OfflineFileViewModel(private val cookie: String) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    var isRefreshing = _isRefreshing.asStateFlow()

    private val _offlineFile = MutableStateFlow(arrayListOf<OfflineTask>())
    var offlineFile = _offlineFile.asStateFlow()

    private val _offlineInfo = MutableStateFlow(OfflineInfo())
    var offlineInfo = _offlineInfo.asStateFlow()

    private val _quotaBean = MutableStateFlow(QuotaBean(1500, 1500))
    var quotaBean = _quotaBean.asStateFlow()

    lateinit var offlineTask: OfflineTask

    private val dialogEventRepository = DialogEventRepository.getInstance()

    var isOpenOfflineDialog by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            dialogEventRepository.events.collect { event ->
                when (event) {
                    is DialogEvent.OpenOfflineDialog -> isOpenOfflineDialog = true
                    else -> { /* ignore */
                    }
                }
            }
        }
    }

    var torrentBean by mutableStateOf(TorrentFileBean())
    val torrentBeanCache = hashMapOf<String, TorrentFileBean>()

    private val fileRepository: FileRepository by lazy {
        FileRepository.getInstance(cookie)
    }

    init {
        getOfflineFileList()
    }

    fun getOfflineFileList() {
        if (_offlineFile.value.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            _isRefreshing.value = true
//            val uid = sharedPreferencesUtil.get(ConfigUtil.uid)!!
            val uid = App.uid
            val sign = fileRepository.getOfflineSign().sign
            _offlineInfo.value = fileRepository.getOfflineTaskList(uid, sign)
            setTaskInfo(_offlineInfo.value.tasks)
            _offlineFile.value = _offlineInfo.value.tasks
            _isRefreshing.value = false
        }
    }

    fun getTorrentTask(sha1: String) {
        //clear torrent bean
        torrentBean = TorrentFileBean()
        if (torrentBeanCache.contains(sha1)) {
            torrentBean = torrentBeanCache[sha1]!!
            return
        }
        viewModelScope.launch {
            val sign = fileRepository.getOfflineSign().sign
            val torrentTask = fileRepository.getOfflineTorrentTaskList(sha1, sign, App.uid)
            XLog.d("getTorrentTask $torrentTask")
            if (!torrentTask.state) {
                App.instance.toast(torrentTask.errorMessage)
                return@launch
            }
            torrentTask.fileSizeString = android.text.format.Formatter.formatFileSize(
                App.instance, torrentTask.fileSize
            ) + " "
//            torrentTask.torrentFileListWeb.removeIf { i -> i.wanted == -1 }
            torrentTask.torrentFileListWeb.forEach { b ->
                b.sizeString = android.text.format.Formatter.formatFileSize(
                    App.instance, b.size
                ) + " "
            }
            torrentBeanCache[sha1] = torrentTask
            //1是选中的，0的未选中的，-1是_____padding_file_0_如果您看到此文件，请升级到BitComet(比特彗星)0.85或以上版本____
            torrentTask.torrentFileListWeb.removeIf { f -> f.wanted == -1 }

            // test
//            val subList = ArrayList(torrentTask.torrentFileListWeb.subList(0, 2))
//            torrentTask.torrentFileListWeb.clear()
//            torrentTask.torrentFileListWeb = subList

            torrentTask.fileCount = torrentTask.torrentFileListWeb.size
            torrentBean = torrentTask
        }
    }

    fun addTorrentTask(
        infoHash: String, savePath: String, wanted: String, handle: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val sign = fileRepository.getOfflineSign().sign
            val addTorrentTask = fileRepository.addOfflineTorrentTask(
                infoHash, wanted, savePath, App.uid, sign
            )
            val message = if (addTorrentTask.state) {
                "任务添加成功，文件已保存至 /云下载/${savePath}"
            } else {
                if (addTorrentTask.errorMsg.contains("请验证账号")) {
                    //打开验证页面
//                    fv.selectedItem = ConfigKeyUtil.VERIFY_MAGNET_LINK_ACCOUNT
                    handle.invoke(true)
                }
                "任务添加失败，${addTorrentTask.errorMsg}"
            }
            App.instance.toast(message)
        }
    }

    private fun setTaskInfo(tasks: ArrayList<OfflineTask>) {
        tasks.forEach { offlineTask ->
            offlineTask.timeString =
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                    offlineTask.addTime * 1000
                )
            offlineTask.sizeString = android.text.format.Formatter.formatFileSize(
                App.instance, if (offlineTask.size == -1L) 0L else offlineTask.size
            )
            offlineTask.percentString =
                if (offlineTask.status == -1) "❎下载失败" else "⬇${offlineTask.percentDone.toInt()}%"
        }
    }

    fun refresh() {
        _offlineFile.value = arrayListOf()
        getOfflineFileList()
    }

    fun clearFinish() {
        viewModelScope.launch {
            val clearFinish = fileRepository.clearOfflineFinish()
            val message = if (clearFinish.state) {
                "清除成功"
            } else {
                "清除失败，${clearFinish.errorMsg}"
            }
            App.instance.toast(message)
        }
    }

    fun clearError() {
        viewModelScope.launch {
            val clearError = fileRepository.clearOfflineError()
            val message = if (clearError.state) {
                "清除成功"
            } else {
                "清除失败，${clearError.errorMsg}"
            }
            App.instance.toast(message)
        }
    }

    fun quota() {
        viewModelScope.launch {
            _quotaBean.value = fileRepository.quota()
        }
    }


    /**
     * savepath:
    wp_path_id:currentCid
    url[0]:xxxxx
    url[1]:xxxxx
    uid:xxxx
    sign:xxxxxxx
    time:1675155957
     */
    fun addTask(list: List<String>, currentCid: String, handle: (Boolean) -> Unit) {
        viewModelScope.launch {
            fileRepository.addOfflineTask(list, currentCid, handle)
        }
    }

    fun openOfflineDialog(index: Int) {
        isOpenOfflineDialog = true
        offlineTask = _offlineFile.value[index]
    }

    fun closeOfflineDialog() {
        isOpenOfflineDialog = false
    }

    fun delete(offlineTask: OfflineTask) {
        viewModelScope.launch {
            val map = hashMapOf("hash[0]" to offlineTask.infoHash)
//            map["uid"] = sharedPreferencesUtil.get(ConfigUtil.uid)!!
            map["uid"] = DataStoreUtil.getData(ConfigKeyUtil.UID, "")
            map["sign"] = fileRepository.getOfflineSign().sign
            map["time"] = (System.currentTimeMillis() / 1000).toString()
            val deleteTask = fileRepository.deleteOfflineTask(map)
            val message = if (deleteTask.state) {
                refresh()
                "删除成功"
            } else {
                "删除失败，${deleteTask.errorMsg}"
            }
            App.instance.toast(message)
        }
    }
}