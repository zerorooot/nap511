package github.zerorooot.nap511.viewmodel

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileDialogState

// ==================== 公开方法：供外部触发对话框事件 ====================

internal fun FileViewModel.openCreateFolderDialog() {
    updateDialogState { copy(activeDialog = FileDialogState.CreateFolder) }
}

internal fun FileViewModel.openSearchDialog() {
    updateDialogState { copy(activeDialog = FileDialogState.Search) }
}

internal fun FileViewModel.openRenameFileDialog(fileBean: FileBean) {
    updateDialogState {
        copy(
            activeDialog = FileDialogState.RenameFile,
            targetFileBean = fileBean
        )
    }
}

internal fun FileViewModel.openFileOrderDialog() {
    updateDialogState { copy(activeDialog = FileDialogState.FileOrder) }
}

internal fun FileViewModel.openAria2Dialog() {
    updateDialogState { copy(activeDialog = FileDialogState.Aria2) }
}

internal fun FileViewModel.openUnzipAllFileDialog() {
    updateDialogState { copy(activeDialog = FileDialogState.UnzipAllFile) }
}


internal fun FileViewModel.openUnzipPasswordDialog(fileBean: FileBean) {
    updateDialogState {
        copy(
            activeDialog = FileDialogState.UnzipPassword,
            targetFileBean = fileBean
        )
    }
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

internal fun FileViewModel.closeUnzipAllFileDialog() = closeDialog()

internal fun FileViewModel.closeCreateSelectTorrentFileDialog() = closeDialog()
