package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.util.DialogEvent
import kotlinx.coroutines.launch


// ==================== 公开方法：供外部触发对话框事件 ====================

internal fun FileViewModel.openCreateFolderDialog() {
    viewModelScope.launch { dialogEventBus.emit(DialogEvent.OpenCreateFolder) }
}

internal fun FileViewModel.openSearchDialog() {
    viewModelScope.launch { dialogEventBus.emit(DialogEvent.OpenSearch) }
}

internal fun FileViewModel.openRenameFileDialog() {
    viewModelScope.launch { dialogEventBus.emit(DialogEvent.OpenRenameFile) }
}

internal fun FileViewModel.openFileInfoDialog() {
    viewModelScope.launch { dialogEventBus.emit(DialogEvent.OpenFileInfo) }
}

internal fun FileViewModel.openFileOrderDialog() {
    viewModelScope.launch { dialogEventBus.emit(DialogEvent.OpenFileOrder) }
}

internal fun FileViewModel.openAria2Dialog() {
    viewModelScope.launch { dialogEventBus.emit(DialogEvent.OpenAria2Dialog) }
}

internal fun FileViewModel.openUnzipAllFileDialog() {
    viewModelScope.launch { dialogEventBus.emit(DialogEvent.OpenUnzipAllFileDialog) }
}

internal fun FileViewModel.openCreateSelectTorrentFileDialog() {
    viewModelScope.launch { dialogEventBus.emit(DialogEvent.OpenCreateSelectTorrentFileDialog) }
}

internal fun FileViewModel.openUnzipDialog() {
    viewModelScope.launch { dialogEventBus.emit(DialogEvent.OpenUnzipDialog) }
}

internal fun FileViewModel.openUnzipPasswordDialog() {
    viewModelScope.launch { dialogEventBus.emit(DialogEvent.OpenUnzipPasswordDialog) }
}

internal fun FileViewModel.openTextBodyDialog() {
    viewModelScope.launch { dialogEventBus.emit(DialogEvent.OpenTextBodyDialog) }
}

// ==================== 关闭方法（直接在本地设 false） ====================

// ==================== 关闭方法（直接在本地设 false） ====================

internal fun FileViewModel.closeCreateFolderDialog() {
    isOpenCreateFolderDialog = false
}

internal fun FileViewModel.closeSearchDialog() {
    isOpenSearchDialog = false
}

internal fun FileViewModel.closeRenameFileDialog() {
    isOpenRenameFileDialog = false
}

internal fun FileViewModel.closeFileInfoDialog() {
    isOpenFileInfoDialog = false
}

internal fun FileViewModel.closeFileOrderDialog() {
    isOpenFileOrderDialog = false
}

internal fun FileViewModel.closeAria2Dialog() {
    isOpenAria2Dialog = false
}

internal fun FileViewModel.closeUnzipDialog() {
    isOpenUnzipDialog = false
}

internal fun FileViewModel.closeUnzipPasswordDialog() {
    isOpenUnzipPasswordDialog = false
}

internal fun FileViewModel.closeTextBodyDialog() {
    isOpenTextBodyDialog = false
}

internal fun FileViewModel.closeUnzipAllFileDialog() {
    isOpenUnzipAllFileDialog = false
}

internal fun FileViewModel.closeCreateSelectTorrentFileDialog() {
    isOpenCreateSelectTorrentFileDialog = false
}