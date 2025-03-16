package github.zerorooot.nap511.viewmodel


import android.app.Application
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
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.DialogSwitchUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

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

    lateinit var offlineTask: OfflineTask

    val dialogSwitchUtil = DialogSwitchUtil.getInstance()


    var torrentBean by mutableStateOf(TorrentFileBean())

    private val fileRepository: FileRepository by lazy {
        FileRepository.getInstance(cookie)
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
        viewModelScope.launch {
            //clear torrent bean
            torrentBean = TorrentFileBean()
            val sign = fileRepository.getOfflineSign().sign
            val torrentTask = fileRepository.getOfflineTorrentTaskList(sha1, sign, App.uid)
            XLog.d("getTorrentTask $torrentTask")
            if (!torrentTask.state) {
                App.instance.toast(torrentTask.errorMessage)
                return@launch
            }
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
            val sign = fileRepository.getOfflineSign().sign
            val addTorrentTask = fileRepository.addOfflineTorrentTask(
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
    fun addTask(list: List<String>, currentCid: String) {
        viewModelScope.launch {
            fileRepository.addOfflineTask(list, currentCid)
        }
    }

    fun openOfflineDialog(index: Int) {
        dialogSwitchUtil.isOpenOfflineDialog = true
        offlineTask = _offlineFile.value[index]
    }

    fun closeOfflineDialog() {
        dialogSwitchUtil.isOpenOfflineDialog = false
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