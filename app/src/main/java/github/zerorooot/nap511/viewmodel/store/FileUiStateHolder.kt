package github.zerorooot.nap511.viewmodel.store

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileContentUiState
import github.zerorooot.nap511.bean.FileDialogUiState
import github.zerorooot.nap511.bean.FileListSource
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.util.formatFileBeanList
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 统一文件 UI 状态持有者（SSOT 唯一真相源）
 * 彻底隔离文件列表浏览状态流与弹窗独立交互状态流，
 * 封装线程安全的原语 Reducer 更新方法与便捷只读访问器。
 */
class FileUiStateHolder(stringProvider: AppStringProvider) {
    /** 1. 文件列表核心浏览与模式状态流 */
    private val _contentState = MutableStateFlow(
        createInitialContentState(stringProvider)
    )
    val contentState: StateFlow<FileContentUiState> = _contentState.asStateFlow()

    /** 2. 弹窗交互独立状态流（与列表展示彻底物理隔离） */
    private val _dialogState = MutableStateFlow(FileDialogUiState())
    val dialogState: StateFlow<FileDialogUiState> = _dialogState.asStateFlow()

    val currentContentState: FileContentUiState
        get() = _contentState.value

    val currentDialogState: FileDialogUiState
        get() = _dialogState.value

    /** 内部状态更新减速器助手 */
    fun updateContentState(reducer: FileContentUiState.() -> FileContentUiState) {
        _contentState.update(reducer)
    }

    /** 弹窗状态更新减速器助手 */
    fun updateDialogState(reducer: FileDialogUiState.() -> FileDialogUiState) {
        _dialogState.update(reducer)
    }

    /** 设置下拉刷新状态 */
    fun setRefreshing(isRefreshing: Boolean) {
        _contentState.update { it.copy(isRefreshing = isRefreshing) }
    }

    /** 关闭当前弹窗并重置弹窗状态 */
    fun closeDialog() {
        _dialogState.update { FileDialogUiState() }
    }

    companion object {
        /**
         * 创建初始 UI 状态：
         * 【冷启动性能优化】：若 FileCacheManager 已经在预加载阶段将根目录 "0" 读入内存缓存，
         * 则直接首帧装填，避免启动后因异步读取产生空白列表闪烁及二次全量重组。
         */
        fun createInitialContentState(stringProvider: AppStringProvider): FileContentUiState {
            val folderSource = FileListSource.Folder("0", stringProvider)
            val cached = runCatching { FileCacheManager.getDate("0") }.getOrNull()
            if (cached != null) {
                XLog.d("FileUiStateHolder: Hit root memory cache on init, items=${cached.fileBeanList.size}")
                val formattedList = formatFileBeanList(cached.fileBeanList)
                return FileContentUiState(
                    source = folderSource,
                    fileBeanList = formattedList,
                    currentCid = "0",
                    totalCount = cached.count,
                    orderBean = OrderBean.from(cached),
                    pathList = cached.path,
                    isRefreshing = false,
                    isLongClickState = false,
                    appBarTitle = folderSource.appName
                )
            }
            return FileContentUiState(
                appBarTitle = stringProvider.defaultAppName,
                source = folderSource
            )
        }
    }
}
