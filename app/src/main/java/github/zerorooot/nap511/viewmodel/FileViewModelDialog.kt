package github.zerorooot.nap511.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.repository.DialogEvent
import kotlinx.coroutines.launch


// ==================== 公开方法：供外部触发对话框事件 ====================

internal fun FileViewModel.openCreateFolderDialog() {
    viewModelScope.launch { dialogEventRepository.emit(DialogEvent.OpenCreateFolder) }
}

internal fun FileViewModel.openSearchDialog() {
    viewModelScope.launch { dialogEventRepository.emit(DialogEvent.OpenSearch) }
}

internal fun FileViewModel.openRenameFileDialog() {
    viewModelScope.launch { dialogEventRepository.emit(DialogEvent.OpenRenameFile) }
}

internal fun FileViewModel.openFileInfoDialog() {
    viewModelScope.launch { dialogEventRepository.emit(DialogEvent.OpenFileInfo) }
}

internal fun FileViewModel.openFileOrderDialog() {
    viewModelScope.launch { dialogEventRepository.emit(DialogEvent.OpenFileOrder) }
}

internal fun FileViewModel.openAria2Dialog() {
    viewModelScope.launch { dialogEventRepository.emit(DialogEvent.OpenAria2Dialog) }
}

internal fun FileViewModel.openUnzipAllFileDialog() {
    viewModelScope.launch { dialogEventRepository.emit(DialogEvent.OpenUnzipAllFileDialog) }
}

internal fun FileViewModel.openCreateSelectTorrentFileDialog() {
    viewModelScope.launch { dialogEventRepository.emit(DialogEvent.OpenCreateSelectTorrentFileDialog) }
}

internal fun FileViewModel.openUnzipDialog() {
    viewModelScope.launch { dialogEventRepository.emit(DialogEvent.OpenUnzipDialog) }
}

internal fun FileViewModel.openUnzipPasswordDialog() {
    viewModelScope.launch { dialogEventRepository.emit(DialogEvent.OpenUnzipPasswordDialog) }
}

internal fun FileViewModel.openTextBodyDialog() {
    viewModelScope.launch { dialogEventRepository.emit(DialogEvent.OpenTextBodyDialog) }
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