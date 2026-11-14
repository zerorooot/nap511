package github.zerorooot.nap511.ui.screen.file

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elvishew.xlog.XLog
import com.google.gson.JsonParser
import github.zerorooot.nap511.ui.model.FileDialogState
import github.zerorooot.nap511.ui.model.FileListSource
import github.zerorooot.nap511.ui.navigation.Route
import github.zerorooot.nap511.ui.model.SettingUiState
import github.zerorooot.nap511.data.model.uniqueId
import github.zerorooot.nap511.ui.dialog.Aria2Dialog
import github.zerorooot.nap511.ui.dialog.BatchRenameDialog
import github.zerorooot.nap511.ui.dialog.CreateFolderDialog
import github.zerorooot.nap511.ui.dialog.CreateSelectTorrentFileDialog
import github.zerorooot.nap511.ui.dialog.DiscardVideosDialog
import github.zerorooot.nap511.ui.dialog.FileInfoDialog
import github.zerorooot.nap511.ui.dialog.FileOrderDialog
import github.zerorooot.nap511.ui.dialog.RenameFileDialog
import github.zerorooot.nap511.ui.dialog.SearchDialog
import github.zerorooot.nap511.ui.dialog.UnzipAllFile
import github.zerorooot.nap511.ui.dialog.UnzipDialog
import github.zerorooot.nap511.ui.dialog.UnzipPassword
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.ui.viewmodel.FileViewModel

@ExperimentalMaterial3Api
@Composable
fun CreateDialogs(
    fileViewModel: FileViewModel,
    settingUiState: SettingUiState,
    onNav: (Route) -> Unit
) {
    val dialogState by fileViewModel.dialogState.collectAsStateWithLifecycle()
    val targetBean = dialogState.targetFileBean

    when (dialogState.activeDialog) {
        // 重命名
        is FileDialogState.RenameFile -> {
            val name = targetBean?.name.orEmpty()
            RenameFileDialog(name, settingUiState.positionAfterAt) {
                if (!it.isNullOrEmpty()) {
                    fileViewModel.rename(targetBean, it)
                }
                fileViewModel.closeRenameFileDialog()
            }
        }
        // 新建文件夹
        is FileDialogState.CreateFolder -> {
            CreateFolderDialog {
                if (!it.isNullOrEmpty()) {
                    fileViewModel.createFolder(it)
                }
                fileViewModel.closeCreateFolderDialog()
            }
        }
        // 文件信息
        is FileDialogState.FileInfo -> {
            if (targetBean != null) {
                FileInfoDialog(targetBean, dialogState.fileInfo) {
                    if (it == null) {
                        fileViewModel.closeFileInfoDialog()
                        return@FileInfoDialog
                    }
                    if (fileViewModel.isQueryState) {
                        fileViewModel.recoverFromLongPress()
                    }
                    fileViewModel.getFiles(it)
                    fileViewModel.closeFileInfoDialog()
                }
            } else {
                fileViewModel.closeFileInfoDialog()
            }
        }

        is FileDialogState.FileOrder -> {
            // 文件排序
            val orderBean = fileViewModel.currentOrderBean
            val currentFcMix = fileViewModel.currentFilesBeanFcMix
            val showStarOrder =
                fileViewModel.contentState.collectAsStateWithLifecycle().value.source is FileListSource.Star
            FileOrderDialog(
                selectOrderBean = orderBean,
                initialFcMix = currentFcMix,
                showStarOrder = showStarOrder
            ) { newOrder, newFcMix ->
                fileViewModel.closeFileOrderDialog()
                if (newOrder != null) {
                    fileViewModel.updateOrder(newOrder, newFcMix)
                }
            }
        }

        is FileDialogState.Aria2 -> {
            val aria2Url = settingUiState.aria2Url
            Aria2Dialog(
                context = aria2Url
            ) {
                fileViewModel.closeAria2Dialog()
                if (it.isNotEmpty()) {
                    val jsonObject = JsonParser.parseString(it).asJsonObject
                    val url = jsonObject.get(ConfigKeyUtil.ARIA2_URL).asString
                    val token = jsonObject.get(ConfigKeyUtil.ARIA2_TOKEN).asString
                    fileViewModel.checkAndSaveAria2(url, token)
                }
            }
        }

        is FileDialogState.Search -> {
            // 搜索
            SearchDialog({
                if (!it.isNullOrEmpty()) {
                    fileViewModel.search(it)
                }
                fileViewModel.closeSearchDialog()
            }) {
                when (it) {
                    "文档" -> fileViewModel.filterFile(1, it)
                    "图片" -> fileViewModel.filterFile(2, it)
                    "音频" -> fileViewModel.filterFile(3, it)
                    "视频" -> fileViewModel.filterFile(4, it)
                    "压缩" -> fileViewModel.filterFile(5, it)
                    "软件" -> fileViewModel.filterFile(6, it)
                }
                fileViewModel.closeSearchDialog()
            }
        }

        is FileDialogState.CreateSelectTorrentFile -> {
            val torrentBean = dialogState.torrentBean
            CreateSelectTorrentFileDialog(
                torrentBean,
            ) { infoHash, savePath, wanted ->
                fileViewModel.closeCreateSelectTorrentFileDialog()
                if (wanted.isEmpty()) {
                    return@CreateSelectTorrentFileDialog
                }
                fileViewModel.addTorrentTask(
                    infoHash, savePath, wanted
                ) {
                    if (it) {
                        onNav.invoke(Route.VerifyMagnetLinkAccount)
                    }
                }
            }
        }

        is FileDialogState.UnzipPassword -> {
            if (targetBean != null) {
                fileViewModel.setRefreshingStatus(false)
                UnzipPassword(targetBean) {
                    XLog.d("云解压 ${targetBean.name} password $it")
                    if (!it.isNullOrEmpty()) {
                        fileViewModel.decryptZip(targetBean, it)
                    } else {
                        fileViewModel.closeUnzipPasswordDialog()
                    }
                }
            } else {
                fileViewModel.closeUnzipPasswordDialog()
            }
        }
        // 解压文件
        is FileDialogState.Unzip -> {
            UnzipDialog(fileViewModel)
        }

        is FileDialogState.UnzipAllFile -> {
            UnzipAllFile(fileViewModel)
        }

        is FileDialogState.BatchRename -> {
            val contentState by fileViewModel.contentState.collectAsStateWithLifecycle()
            val selectedFiles = remember(contentState.fileBeanList, contentState.selectedFileIds) {
                contentState.fileBeanList.filter { it.uniqueId in contentState.selectedFileIds }
            }
            if (selectedFiles.isNotEmpty()) {
                BatchRenameDialog(
                    targetFiles = selectedFiles,
                    onDismissRequest = { fileViewModel.closeBatchRenameDialog() },
                    onConfirm = { renameMap ->
                        fileViewModel.batchRename(renameMap)
                        fileViewModel.closeBatchRenameDialog()
                    }
                )
            } else {
                fileViewModel.closeBatchRenameDialog()
            }
        }

        // 移除确认删除弹窗
        is FileDialogState.DiscardVideos -> {
            val discardList = dialogState.discardVideoList
            if (discardList.isNotEmpty()) {
                DiscardVideosDialog(
                    discardList = discardList,
                    onConfirm = {
                        fileViewModel.closeDiscardVideosDialog()
                        fileViewModel.confirmDiscardVideos(discardList, dialogState.targetCid)
                    },
                    onDismiss = {
                        fileViewModel.closeDiscardVideosDialog()
                        // 用户点击“取消”或点击遮罩外部：更新本地进度缓存并同步 115 云端历史
                        fileViewModel.cancelDiscardVideos(discardList, dialogState.targetCid)
                    }
                )
            } else {
                fileViewModel.closeDiscardVideosDialog()
            }
        }

        else -> {
            fileViewModel.closeDialog()
        }
    }
}
