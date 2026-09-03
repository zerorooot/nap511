package github.zerorooot.nap511.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.bean.OfflineInfo
import github.zerorooot.nap511.bean.OfflineListCount
import github.zerorooot.nap511.bean.OfflineTask
import github.zerorooot.nap511.bean.OfflineTaskType
import github.zerorooot.nap511.bean.QuotaBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.DialogEvent
import github.zerorooot.nap511.util.DialogEventBus
import github.zerorooot.nap511.util.onFailureToastAndLog
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class OfflineFileViewModel(private val cookie: String) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    var isRefreshing = _isRefreshing.asStateFlow()

    private var downloadingTask by mutableStateOf(OfflineInfo())
    private val _downloadingList = MutableStateFlow<List<OfflineTask>>(emptyList())
    val downloadingList = _downloadingList.asStateFlow()

    private var failedTask by mutableStateOf(OfflineInfo())
    private val _failedList = MutableStateFlow<List<OfflineTask>>(emptyList())
    val failedList = _failedList.asStateFlow()

    private var completedTask by mutableStateOf(OfflineInfo())
    private val _completedList = MutableStateFlow<List<OfflineTask>>(emptyList())
    val completedList = _completedList.asStateFlow()


    private val _offlineInfo = MutableStateFlow(OfflineListCount())
    var offlineInfo = _offlineInfo.asStateFlow()

    private val _quotaBean = MutableStateFlow(QuotaBean(1500, 1500))
    var quotaBean = _quotaBean.asStateFlow()

    lateinit var offlineTask: OfflineTask
    val urlText = mutableStateOf("")

    private val dialogEventBus = DialogEventBus.getInstance()

    var isOpenOfflineDialog by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            dialogEventBus.events.collect { event ->
                when (event) {
                    is DialogEvent.OpenOfflineDialog -> isOpenOfflineDialog = true
                    else -> { /* ignore */
                    }
                }
            }
        }
    }


    private val fileRepository: FileRepository by lazy {
        FileRepository.getInstance(cookie)
    }

    /**
     * 首次进入页面时加载（若三项均为空则触发请求）
     */
    fun getOfflineFileList() {
        if (_downloadingList.value.isNotEmpty() ||
            _failedList.value.isNotEmpty() ||
            _completedList.value.isNotEmpty()
        ) {
            return
        }
        refresh()
    }

    /**
     * 并行请求获取三类列表数据
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching {
                val uid = App.uid
                val sign = fileRepository.getOfflineSign().sign

                // 1. 并行发起网络请求并直接在内部处理格式化，减少 refresh 函数内的重置逻辑
                val infoDeferred = async { fileRepository.getOfflineTaskCount() }
                val downloadingDeferred =
                    async { fetchTaskListAndProcess(uid, sign, OfflineTaskType.DownloadingList) }
                val failedDeferred =
                    async { fetchTaskListAndProcess(uid, sign, OfflineTaskType.FailedList) }
                val completedDeferred =
                    async { fetchTaskListAndProcess(uid, sign, OfflineTaskType.CompletedList) }

                // 2. 集中等待结果
                val downloadingRes = downloadingDeferred.await()
                val failedRes = failedDeferred.await()
                val completedRes = completedDeferred.await()
                val infoRes = infoDeferred.await()

                // 3. 分离并统一更新 UI 状态
                updateTasksState(downloadingRes, failedRes, completedRes)

                _offlineInfo.value = infoRes
            }.onFailureToastAndLog(tag = "OfflineFileViewModel", customMsg = "刷新离线任务列表失败")
            _isRefreshing.value = false
        }
    }

    /**
     * 拆分提取：拉取指定类型任务列表并格式化数据
     */
    private suspend fun fetchTaskListAndProcess(
        uid: String,
        sign: String,
        type: OfflineTaskType
    ): OfflineInfo {
        val result = fileRepository.getOfflineTaskList(uid, sign, 1, type)
        setTaskInfo(result.tasks)
        return result
    }

    /**
     * 拆分提取：更新各项列表的状态
     */
    private fun updateTasksState(
        downloading: OfflineInfo,
        failed: OfflineInfo,
        completed: OfflineInfo
    ) {
        downloadingTask = downloading
        _downloadingList.value = downloading.tasks

        failedTask = failed
        _failedList.value = failed.tasks

        completedTask = completed
        _completedList.value = completed.tasks
    }

    /**
     * 分页加载更多任务列表（通用函数）
     */
    fun getMoreTaskList(offlineInfo: OfflineInfo, type: OfflineTaskType) {
        if (offlineInfo.isLoadedComplete) return
        // 1. 读取对应类型的分页状态
        val (currentPage, maxPage) = Pair(
            offlineInfo.page,
            offlineInfo.pageCount
        )
        if (maxPage == 1 || _isRefreshing.value) {
            return
        }
        if (currentPage == maxPage) {
            App.instance.toast("加载完毕")
            offlineInfo.isLoadedComplete = true
            return
        }

        _isRefreshing.value = true
        viewModelScope.launch {
            runCatching {
                val uid = App.uid
                val sign = fileRepository.getOfflineSign().sign
                val nextPage = currentPage + 1
                val res = fileRepository.getOfflineTaskList(uid, sign, nextPage, type)
                if (res.state) {
                    // 仅处理新拉取的数据，降低重复格式化开销
                    setTaskInfo(res.tasks)
                    // 5. 追加新数据并更新页码
                    applyTaskResult(type, res)
                }
            }.onFailureToastAndLog(tag = "OfflineFileViewModel", customMsg = "加载下一页失败")
            _isRefreshing.value = false
        }
    }

    // 辅助方法：更新对应的 List StateFlow 与页数状态
    private fun applyTaskResult(
        type: OfflineTaskType,
        offlineInfo: OfflineInfo
    ) {
        val newTasks = offlineInfo.tasks
        when (type) {
            OfflineTaskType.DownloadingList -> {
                downloadingTask = offlineInfo
                _downloadingList.update { it + newTasks }
            }

            OfflineTaskType.FailedList -> {
                failedTask = offlineInfo
                _failedList.update { it + newTasks }
            }

            OfflineTaskType.CompletedList -> {
                completedTask = offlineInfo
                _completedList.update { it + newTasks }
            }
        }
    }

    // 供外部快捷调用的 3 个方法
    fun loadMoreDownloadingTasks() =
        getMoreTaskList(downloadingTask, OfflineTaskType.DownloadingList)

    fun loadMoreFailedTasks() = getMoreTaskList(failedTask, OfflineTaskType.FailedList)
    fun loadMoreCompletedTasks() = getMoreTaskList(completedTask, OfflineTaskType.CompletedList)

    private fun setTaskInfo(tasks: List<OfflineTask>) {
        tasks.forEach { offlineTask ->
            offlineTask.timeString =
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                    offlineTask.addTime * 1000
                )
            offlineTask.sizeString = android.text.format.Formatter.formatFileSize(
                App.instance, if (offlineTask.size == -1L) 0L else offlineTask.size
            )
            offlineTask.percentString = when (offlineTask.status) {
                2 -> {
                    //下载成功
                    "✅下载成功"
                }

                -1 -> {
                    "❎下载失败"
                }

                else -> {
                    "⬇${offlineTask.percentDone.toInt()}%"
                }
            }
        }
    }

    fun clearFinish() {
        viewModelScope.launch {
            runCatching {
                val clearFinish = fileRepository.clearOfflineFinish()
                if (clearFinish.state) {
                    refresh()
                    "清除成功"
                } else {
                    "清除失败，${clearFinish.errorMsg}"
                }
            }.onSuccess { message ->
                App.instance.toast(message)
            }.onFailureToastAndLog(tag = "OfflineFileViewModel")
        }
    }

    fun clearError() {
        viewModelScope.launch {
            runCatching {
                val clearError = fileRepository.clearOfflineError()
                if (clearError.state) {
                    refresh()
                    "清除成功"
                } else {
                    "清除失败，${clearError.errorMsg}"
                }
            }.onSuccess { message ->
                App.instance.toast(message)
            }.onFailureToastAndLog(tag = "OfflineFileViewModel")
        }
    }

    fun quota() {
        viewModelScope.launch {
            runCatching {
                fileRepository.quota()
            }.onSuccess { quotaData ->
                _quotaBean.value = quotaData
            }.onFailureToastAndLog(tag = "OfflineFileViewModel")
        }
    }

    fun addTask(list: List<String>, currentCid: String, handle: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching {
                fileRepository.addOfflineTask(list, currentCid, handle)
            }.onFailureToastAndLog(tag = "OfflineFileViewModel")
        }
    }

    /**
     * 3. 支持直接传入 OfflineTask 打开详情弹窗
     */
    fun openOfflineDialog(task: OfflineTask) {
        isOpenOfflineDialog = true
        offlineTask = task
    }

    fun closeOfflineDialog() {
        isOpenOfflineDialog = false
    }

    fun delete(offlineTask: OfflineTask) {
        viewModelScope.launch {
            runCatching {
                val map = hashMapOf("hash[0]" to offlineTask.infoHash)
                map["uid"] = DataStoreUtil.getData(ConfigKeyUtil.UID, "")
                map["sign"] = fileRepository.getOfflineSign().sign
                map["time"] = (System.currentTimeMillis() / 1000).toString()
                val deleteTask = fileRepository.deleteOfflineTask(map)
                if (deleteTask.state) {
                    refresh()
                    "删除成功"
                } else {
                    "删除失败，${deleteTask.errorMsg}"
                }
            }.onSuccess { message ->
                App.instance.toast(message)
            }.onFailureToastAndLog(tag = "OfflineFileViewModel")
        }
    }
}