package github.zerorooot.nap511.viewmodel


import android.app.Activity
import android.app.Application
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.bean.OfflineInfo
import github.zerorooot.nap511.bean.OfflineTask
import github.zerorooot.nap511.bean.QuotaBean
import github.zerorooot.nap511.service.FileService
import github.zerorooot.nap511.service.OfflineService
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigUtil
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
            val uid = DataStoreUtil.getData(ConfigUtil.uid, "")
            val sign = offlineService.getSign().sign
            _offlineInfo.value = offlineService.taskList(uid, sign)
            setTaskInfo(_offlineInfo.value.tasks)
            _offlineFile.value = _offlineInfo.value.tasks
            _isRefreshing.value = false
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
    fun addTask(list: List<String>, currentCid: String, close: Boolean = false) {
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
            map["uid"] = DataStoreUtil.getData(ConfigUtil.uid, "")
            map["sign"] = offlineService.getSign().sign
            map["time"] = (System.currentTimeMillis() / 1000).toString()
            list.forEachIndexed { index, s ->
                map["url[$index]"] = s
            }
            val addTask = offlineService.addTask(map)
            val message = if (addTask.state) {
                "任务添加成功"
            } else {
                if (addTask.errorMsg.contains("请验证账号") && !close) {
                    App.captchaUrl =
                        "https://captchaapi.115.com/?ac=security_code&type=web&cb=Close911_" + System.currentTimeMillis()
                    //打开验证页面
                    App.selectedItem = "captchaWebView"
                }
                //把失败的离线链接保存起来
                val currentOfflineTaskList =
                    DataStoreUtil.getData(ConfigUtil.currentOfflineTask, "")
                        .split("\n")
                        .filter { i -> i != "" && i != " " }
                        .toSet()
                        .toMutableList()
                currentOfflineTaskList.addAll(list)
                val stringJoiner = StringJoiner("\n")
                currentOfflineTaskList.toSet().forEach { stringJoiner.add(it) }
                //写入缓存
                DataStoreUtil.putData(
                    ConfigUtil.currentOfflineTask,
                    stringJoiner.toString()
                )
                //记录当前失败的cid
                DataStoreUtil.putData(
                    ConfigUtil.errorDownloadCid,
                    currentCid
                )
                "任务添加失败，${addTask.errorMsg}"
            }
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
            if (close && message == "任务添加成功") {
                exitProcess(0);
            }
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
            map["uid"] = DataStoreUtil.getData(ConfigUtil.uid, "")
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