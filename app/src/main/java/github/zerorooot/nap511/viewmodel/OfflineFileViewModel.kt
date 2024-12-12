package github.zerorooot.nap511.viewmodel


import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.bean.OfflineInfo
import github.zerorooot.nap511.bean.OfflineTask
import github.zerorooot.nap511.bean.QuotaBean
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.service.FileService
import github.zerorooot.nap511.service.OfflineService
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.StringJoiner
import kotlin.system.exitProcess

class OfflineFileViewModel(private val cookie: String, private val application: Application) :
    ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    var isRefreshing = _isRefreshing.asStateFlow()

    private val _offlineFile = MutableStateFlow(arrayListOf<OfflineTask>())
    var offlineFile = _offlineFile.asStateFlow()

    private val _offlineInfo = MutableStateFlow(OfflineInfo())
    var offlineInfo = _offlineInfo.asStateFlow()

    private val _quotaBean = MutableStateFlow(QuotaBean(1500, 1500))
    var quotaBean = _quotaBean.asStateFlow()

    private val _isOpenOfflineDialog = MutableStateFlow(false)
    var isOpenOfflineDialog = _isOpenOfflineDialog.asStateFlow()

    lateinit var offlineTask: OfflineTask

    //    val addTaskReturn = MutableLiveData<Pair<Boolean, String>>()
    var addTaskReturn by mutableStateOf(Pair<Boolean, String>(false, "通知信息未初始化，添加失败～"))



    var torrentBean by mutableStateOf(TorrentFileBean())

    //打开对话框相关
    var isOpenCreateSelectTorrentFileDialog by mutableStateOf(false)

    private val offlineService: OfflineService by lazy {
        OfflineService.getInstance(cookie)
    }

    private val fileService: FileService by lazy {
        FileService.getInstance(cookie)
    }

    fun getOfflineFileList() {
        if (_offlineFile.value.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            _isRefreshing.value = true
//            val uid = sharedPreferencesUtil.get(ConfigUtil.uid)!!
            val uid = App.uid
            val sign = offlineService.getSign().sign
            _offlineInfo.value = offlineService.taskList(uid, sign)
            setTaskInfo(_offlineInfo.value.tasks)
            _offlineFile.value = _offlineInfo.value.tasks
            _isRefreshing.value = false
        }
    }

    fun getTorrentTask(sha1: String) {
        viewModelScope.launch {
            //clear torrent bean
            torrentBean = TorrentFileBean()
            val sign = offlineService.getSign().sign
            val torrentTask = offlineService.getTorrentTaskList(sha1, App.uid, sign)
            torrentTask.fileSizeString = android.text.format.Formatter.formatFileSize(
                application, torrentTask.fileSize
            ) + " "
//            torrentTask.torrentFileListWeb.removeIf { i -> i.wanted == -1 }
            torrentTask.torrentFileListWeb.forEach { b ->
                b.sizeString = android.text.format.Formatter.formatFileSize(
                    application, b.size
                ) + " "
            }
            torrentBean = torrentTask
        }
    }

    fun addTorrentTask(torrentFileBean: TorrentFileBean, wanted: String) {
        viewModelScope.launch {
            val sign = offlineService.getSign().sign
            val addTorrentTask = offlineService.addTorrentTask(
                torrentFileBean.infoHash,
                wanted,
                torrentFileBean.torrentName,
                App.uid,
                sign
            )
            val message = if (addTorrentTask.state) {
                "任务添加成功，文件已保存至 /云下载/${torrentFileBean.torrentName}"
            } else {
                if (addTorrentTask.errorMsg.contains("请验证账号")) {
                    //打开验证页面
                    App.selectedItem = ConfigKeyUtil.VERIFY_MAGNET_LINK_ACCOUNT
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
                application, if (offlineTask.size == -1L) 0L else offlineTask.size
            )
            offlineTask.percentString =
                if (offlineTask.status == -1) "❎下载失败" else "⬇${offlineTask.percentDone.toInt()}%"
        }
    }

    fun refresh() {
        _offlineFile.value.clear()
        getOfflineFileList()
    }

    fun clearFinish() {
        viewModelScope.launch {
            val clearFinish = offlineService.clearFinish()
            val message = if (clearFinish.state) {
                "清除成功"
            } else {
                "清除失败，${clearFinish.errorMsg}"
            }
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun clearError() {
        viewModelScope.launch {
            val clearError = offlineService.clearError()
            val message = if (clearError.state) {
                "清除成功"
            } else {
                "清除失败，${clearError.errorMsg}"
            }
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun quota() {
        viewModelScope.launch {
            _quotaBean.value = offlineService.quota()
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
    fun addTask(list: List<String>, currentCid: String) {
        viewModelScope.launch {
            val downloadPath = fileService.setDownloadPath(currentCid)
            if (!downloadPath.state) {
                Toast.makeText(
                    application,
                    "设置离线位置失败，默认保存到\"云下载\"目录",
                    Toast.LENGTH_SHORT
                ).show()
            }

            val map = HashMap<String, String>()
            map["savepath"] = ""
            map["wp_path_id"] = currentCid
            map["uid"] = App.uid
            map["sign"] = offlineService.getSign().sign
            map["time"] = (System.currentTimeMillis() / 1000).toString()
            list.forEachIndexed { index, s ->
                map["url[$index]"] = s
            }
            val addTask = offlineService.addTask(map)
            val message = if (addTask.state) {
                "任务添加成功"
            } else {
                if (addTask.errorMsg.contains("请验证账号")) {
                    App.selectedItem = ConfigKeyUtil.VERIFY_MAGNET_LINK_ACCOUNT
                }
                //把失败的离线链接保存起来
                val currentOfflineTaskList =
                    DataStoreUtil.getData(ConfigKeyUtil.CURRENT_OFFLINE_TASK, "")
                        .split("\n")
                        .filter { i -> i != "" && i != " " }
                        .toSet()
                        .toMutableList()
                currentOfflineTaskList.addAll(list)
                val stringJoiner = StringJoiner("\n")
                currentOfflineTaskList.toSet().forEach { stringJoiner.add(it) }
                //写入缓存
                DataStoreUtil.putData(
                    ConfigKeyUtil.CURRENT_OFFLINE_TASK,
                    stringJoiner.toString()
                )
                //记录当前失败的cid
                DataStoreUtil.putData(
                    ConfigKeyUtil.ERROR_DOWNLOAD_CID,
                    currentCid
                )
                "任务添加失败，${addTask.errorMsg}"
            }
            addTaskReturn = Pair<Boolean, String>(addTask.state, message)
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun openOfflineDialog(index: Int) {
        _isOpenOfflineDialog.value = true
        offlineTask = _offlineFile.value[index]
    }

    fun closeOfflineDialog() {
        _isOpenOfflineDialog.value = false
    }

    fun delete(offlineTask: OfflineTask) {
        viewModelScope.launch {
            val map = hashMapOf("hash[0]" to offlineTask.infoHash)
//            map["uid"] = sharedPreferencesUtil.get(ConfigUtil.uid)!!
            map["uid"] = DataStoreUtil.getData(ConfigKeyUtil.UID, "")
            map["sign"] = offlineService.getSign().sign
            map["time"] = (System.currentTimeMillis() / 1000).toString()
            val deleteTask = offlineService.deleteTask(map)
            val message = if (deleteTask.state) {
                refresh()
                "删除成功"
            } else {
                "删除失败，${deleteTask.errorMsg}"
            }
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
        }
    }
}