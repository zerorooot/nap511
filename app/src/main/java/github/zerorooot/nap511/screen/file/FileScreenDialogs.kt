package github.zerorooot.nap511.screen.file

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elvishew.xlog.XLog
import com.google.gson.JsonParser
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.dialog.Aria2Dialog
import github.zerorooot.nap511.dialog.CreateFolderDialog
import github.zerorooot.nap511.dialog.CreateSelectTorrentFileDialog
import github.zerorooot.nap511.dialog.DiscardVideosDialog
import github.zerorooot.nap511.dialog.FileInfoDialog
import github.zerorooot.nap511.dialog.FileOrderDialog
import github.zerorooot.nap511.dialog.RenameFileDialog
import github.zerorooot.nap511.dialog.SearchDialog
import github.zerorooot.nap511.dialog.UnzipAllFile
import github.zerorooot.nap511.dialog.UnzipDialog
import github.zerorooot.nap511.dialog.UnzipPassword
import github.zerorooot.nap511.repository.AuthRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import kotlinx.coroutines.launch

@ExperimentalMaterial3Api
@Composable
fun CreateDialogs(
    fileViewModel: FileViewModel,
    settingUiState: SettingUiState,
    onNav: (Route) -> Unit
) {
    val scope = rememberCoroutineScope()
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
            FileOrderDialog(orderBean) { newOrder ->
                fileViewModel.closeFileOrderDialog()
                if (newOrder != null) {
                    fileViewModel.updateOrder(newOrder)
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
                    scope.launch {
                        AuthRepository.checkAndSaveAria2(url, token)
                            .onSuccess { msg -> App.instance.toast(msg) }
                            .onFailure { err -> App.instance.toast("aria2配置失败: ${err.localizedMessage}") }
                    }
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
