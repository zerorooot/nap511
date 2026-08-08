package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.CategoryDetailResponse
import github.zerorooot.nap511.bean.RepeatFileItem
import github.zerorooot.nap511.bean.RepeatListResponse
import github.zerorooot.nap511.bean.RepeatStatusData
import github.zerorooot.nap511.screen.formatBytes
import github.zerorooot.nap511.service.RepeatService
import github.zerorooot.nap511.util.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RepeatUiState(
    val statusData: RepeatStatusData? = null,
    val fileList: List<RepeatFileItem> = emptyList(),
    val totalCount: Int = 0,
    val offset: Int = 0,
    val isLoadingList: Boolean = false,
    val isRefreshing: Boolean = false,
    val isListEndReached: Boolean = false,
)

class RepeatFileViewModel(
    private val cookie: String
) : ViewModel() {
    private val repeatService: RepeatService by lazy {
        RepeatService.getInstance(cookie)
    }

    private val _uiState = MutableStateFlow(RepeatUiState())
    val uiState: StateFlow<RepeatUiState> = _uiState.asStateFlow()

    // 存储当前选中的文件分类详情（控制弹窗显示）
    private val _categoryDetail = MutableStateFlow<CategoryDetailResponse?>(null)
    val categoryDetail: StateFlow<CategoryDetailResponse?> = _categoryDetail.asStateFlow()

    // 1. 初始化或重新加载全部数据
    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadStatus()
            refreshList()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    // 2. 加载状态
    //{"state":false,"msg":"","msg_code":0,"data":{"group_count":0,"file_count":0,"file_size":0}}
    private suspend fun loadStatus() {
        val repeatStatus = repeatService.getRepeatStatus()
        XLog.d("repeatStatus: $repeatStatus")
        _uiState.update { it.copy(statusData = repeatStatus.data) }

    }


    // 3. 刷新列表（重置 offset 并拉取首页）
    fun refreshList() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingList = true,
                    offset = 0,
                    isListEndReached = false,
                    isRefreshing = true
                )
            }
            val repeatList = getRepeatList(offset = 0)
            //清空list,防止日志里的内容过多
            val copy = repeatList.copy(data = emptyList())
            XLog.d("repeatList: $copy")

            val list = repeatList.data
            val total = repeatList.count.toIntOrNull() ?: 0
            _uiState.update {
                it.copy(
                    fileList = list,
                    totalCount = total,
                    offset = list.size,
                    isLoadingList = false,
                    isRefreshing = false,
                    isListEndReached = list.size >= total || list.isEmpty()
                )
            }
        }

    }


    // 4. 触底加载下一页（分页）
    fun loadNextPage() {
        val currentState = _uiState.value
        if (currentState.isLoadingList || currentState.isListEndReached) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingList = true, isRefreshing = true) }
            val repeatList = getRepeatList(offset = currentState.offset)
            val newList = repeatList.data
            val total = repeatList.count.toIntOrNull() ?: 0
            _uiState.update { state ->
                val updatedList = state.fileList + newList
                state.copy(
                    fileList = updatedList,
                    totalCount = total,
                    offset = updatedList.size,
                    isLoadingList = false,
                    isRefreshing = false,
                    isListEndReached = updatedList.size >= total || newList.isEmpty()
                )
            }
        }

    }


    // 5. 触发强制全盘排重
    fun triggerForceRefresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true
                )
            }
            val forceRefresh = repeatService.forceRefresh()
            XLog.d("forceRefresh: $forceRefresh")
            val message = if (forceRefresh.state) {
                refreshList()
                "已提交全盘排重请求"
            } else {
                forceRefresh.message
            }
            App.instance.toast(message)
            _uiState.update {
                it.copy(
                    isRefreshing = false
                )
            }
        }
    }

    fun dismissCategoryDetail() {
        _categoryDetail.value = null
    }


    // 执行一键去重删除
    fun executeDelete(field: String, order: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            XLog.d("executeDelete: $field $order")
            val deleteRepeatFiles = repeatService.deleteRepeatFiles(field, order)
            val clearEmpty = repeatService.clearEmpty()

            XLog.d("deleteRepeatFiles: $deleteRepeatFiles")
            XLog.d("clearEmpty: $clearEmpty")
            val a = if (deleteRepeatFiles.state) {
                _uiState.update { RepeatUiState() }
                "去重指令执行成功；"
            } else {
                deleteRepeatFiles.message + "；"
            }
            val b = if (clearEmpty.state) {
                "删除空文件夹清空成功，记得刷新页面！"
            } else {
                clearEmpty.message
            }
            App.instance.toast(a + b)

            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun fetchCategoryDetail(cid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val categoryDetail1 = repeatService.getCategoryDetail(cid)
            _categoryDetail.value = categoryDetail1
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun getRepeatList(offset: Int): RepeatListResponse {
        val repeatList = repeatService.getRepeatList(offset)
        repeatList.data.forEach { i ->
            when (i.ico) {
                "mp3" -> i.fileIco = R.drawable.mp3
                "mp4" -> i.fileIco = R.drawable.mp4
                "apk" -> i.fileIco = R.drawable.apk
                "iso" -> i.fileIco = R.drawable.iso
                "zip", "7z", "rar" -> i.fileIco = R.drawable.zip
                "png", "gif", "jpg" -> i.fileIco = R.drawable.png
                "txt" -> i.fileIco = R.drawable.txt
                "torrent" -> i.fileIco = R.drawable.torrent
            }
            i.fileSizeString = formatBytes(i.fileSize.toLongOrNull() ?: 0L) + " "
        }
        return repeatList
    }
}