package github.zerorooot.nap511.viewmodel.store

import github.zerorooot.nap511.bean.FileContentUiState
import github.zerorooot.nap511.bean.FileDialogUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 统一文件 UI 状态持有者（SSOT 唯一真相源）
 * 彻底隔离文件列表浏览状态流与弹窗独立交互状态流，
 * 封装线程安全的原语 Reducer 更新方法与便捷只读访问器。
 */
class FileUiStateHolder(defaultTitle: String) {
    /** 1. 文件列表核心浏览与模式状态流 */
    private val _contentState = MutableStateFlow(FileContentUiState(appBarTitle = defaultTitle))
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
}
