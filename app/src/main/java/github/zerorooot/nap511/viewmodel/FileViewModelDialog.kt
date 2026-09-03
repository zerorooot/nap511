package github.zerorooot.nap511.viewmodel

import github.zerorooot.nap511.bean.FileDialogState


// ==================== 公开方法：供外部触发对话框事件 ====================

internal fun FileViewModel.openCreateFolderDialog() {
    activeDialog = FileDialogState.CreateFolder
}

internal fun FileViewModel.openSearchDialog() {
    activeDialog = FileDialogState.Search
}

internal fun FileViewModel.openRenameFileDialog() {
    activeDialog = FileDialogState.RenameFile
}

internal fun FileViewModel.openFileInfoDialog() {
    activeDialog = FileDialogState.FileInfo
}

internal fun FileViewModel.openFileOrderDialog() {
    activeDialog = FileDialogState.FileOrder
}

internal fun FileViewModel.openAria2Dialog() {
    activeDialog = FileDialogState.Aria2
}

internal fun FileViewModel.openUnzipAllFileDialog() {
    activeDialog = FileDialogState.UnzipAllFile
}

internal fun FileViewModel.openCreateSelectTorrentFileDialog() {
    activeDialog = FileDialogState.CreateSelectTorrentFile
}

internal fun FileViewModel.openUnzipDialog() {
    activeDialog = FileDialogState.Unzip
}

internal fun FileViewModel.openUnzipPasswordDialog() {
    activeDialog = FileDialogState.UnzipPassword
}


// ==================== 关闭方法（统一重定向到 closeDialog） ====================

internal fun FileViewModel.closeCreateFolderDialog() = closeDialog()

internal fun FileViewModel.closeSearchDialog() = closeDialog()

internal fun FileViewModel.closeRenameFileDialog() = closeDialog()

internal fun FileViewModel.closeFileInfoDialog() = closeDialog()

internal fun FileViewModel.closeFileOrderDialog() = closeDialog()

internal fun FileViewModel.closeAria2Dialog() = closeDialog()

internal fun FileViewModel.closeUnzipDialog() = closeDialog()

internal fun FileViewModel.closeUnzipPasswordDialog() = closeDialog()

internal fun FileViewModel.closeTextBodyDialog() = closeDialog()

internal fun FileViewModel.closeUnzipAllFileDialog() = closeDialog()

internal fun FileViewModel.closeCreateSelectTorrentFileDialog() = closeDialog()
