package github.zerorooot.nap511.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.elvishew.xlog.XLog
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.OrderEnum
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.closeAria2Dialog
import github.zerorooot.nap511.viewmodel.closeCreateFolderDialog
import github.zerorooot.nap511.viewmodel.closeCreateSelectTorrentFileDialog
import github.zerorooot.nap511.viewmodel.closeFileInfoDialog
import github.zerorooot.nap511.viewmodel.closeFileOrderDialog
import github.zerorooot.nap511.viewmodel.closeRenameFileDialog
import github.zerorooot.nap511.viewmodel.closeSearchDialog
import github.zerorooot.nap511.viewmodel.closeUnzipPasswordDialog
import github.zerorooot.nap511.viewmodel.createFolder
import github.zerorooot.nap511.viewmodel.decryptZip
import github.zerorooot.nap511.viewmodel.rename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@ExperimentalMaterial3Api
@Composable
fun CreateDialogs(
    fileViewModel: FileViewModel,
    offlineFileViewModel: OfflineFileViewModel,
    onNav: (Route) -> Unit
) {
//    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    when (fileViewModel.activeDialog) {
        //重命名
        is FileDialogState.RenameFile -> {
            val name = fileViewModel.fileBeanList[fileViewModel.selectIndex].name
            RenameFileDialog(name) {
                if (it != null && it != "") {
                    fileViewModel.rename(it)
                }
                fileViewModel.closeRenameFileDialog()
            }
        }
        //新建文件夹
        is FileDialogState.CreateFolder -> {
            CreateFolderDialog {
                if (it != null && it != "") {
                    fileViewModel.createFolder(it)
                }
                fileViewModel.closeCreateFolderDialog()
            }
        }
        //文件信息
        is FileDialogState.FileInfo -> {
            val fileBean = fileViewModel.fileBeanList[fileViewModel.selectIndex]
            val fileInfo = fileViewModel.fileInfo
            FileInfoDialog(fileBean, fileInfo) {
                if (it == null) {
                    fileViewModel.closeFileInfoDialog()
                    return@FileInfoDialog
                }
                if (fileViewModel.isSearchState) {
                    fileViewModel.isSearchState = false
                    fileViewModel.recoverFromLongPress()
                }
                fileViewModel.getFiles(it)
                fileViewModel.closeFileInfoDialog()
            }
        }

        is FileDialogState.FileOrder -> {
            //文件排序
            val orderBean = fileViewModel.orderBean.toString()
            FileOrderDialog(orderBean) {
                fileViewModel.closeFileOrderDialog()
                if (it != "") {
                    val asc = if (it.subSequence(it.length - 2, it.length) == "⬆️") 1 else 0
                    val type = when (it.subSequence(0, it.length - 2)) {
                        "文件名称" -> OrderEnum.name
                        "更改时间" -> OrderEnum.change
                        "文件种类" -> OrderEnum.type
                        "文件大小" -> OrderEnum.size
                        else -> OrderEnum.name
                    }
                    fileViewModel.orderBean = OrderBean(type, asc)
                    fileViewModel.order()

                }
            }
        }

        is FileDialogState.Aria2 -> {
            Aria2Dialog(
                context = DataStoreUtil.getData(
                    ConfigKeyUtil.ARIA2_URL, ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE
                )
            ) {
                fileViewModel.closeAria2Dialog()
                if (it != "") {
                    val jsonObject = JsonParser.parseString(it).asJsonObject
                    val aria2Url = jsonObject.get(ConfigKeyUtil.ARIA2_URL).asString
                    val aria2Token = jsonObject.get(ConfigKeyUtil.ARIA2_TOKEN).asString
                    App.instance.checkAria2(aria2Url, aria2Token)
                }
            }
        }

        is FileDialogState.Search -> {
            //搜索
            SearchDialog({
                if (it != null && it != "") {
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
            val torrentBean = offlineFileViewModel.torrentBean
            CreateSelectTorrentFileDialog(
                torrentBean,
                { fileViewModel.setRefreshingStatus(false) }
            ) { infoHash, savePath, wanted ->
                fileViewModel.closeCreateSelectTorrentFileDialog()
                if (wanted.isEmpty()) {
                    return@CreateSelectTorrentFileDialog
                }
                offlineFileViewModel.addTorrentTask(
                    infoHash, savePath, wanted
                ) {
                    if (it) {
                        onNav.invoke(Route.VerifyMagnetLinkAccount)
                    }
                }
            }
        }

        is FileDialogState.UnzipPassword -> {
            val fileBean = fileViewModel.fileBeanList[fileViewModel.selectIndex]
            fileViewModel.setRefreshingStatus(false)
            UnzipPassword(fileBean) {
                XLog.d("云解压 ${fileBean.name} password $it")
                if (it != null && it != "") {
                    fileViewModel.decryptZip(it)
                } else {
                    fileViewModel.closeUnzipPasswordDialog()
                }
            }
        }
        //解压文件
        is FileDialogState.Unzip -> {
            UnzipDialog(fileViewModel)
        }

        is FileDialogState.UnzipAllFile -> {
            UnzipAllFile(fileViewModel)
        }

        is FileDialogState.TextBody -> {
            TextBodyDialog(fileViewModel)
        }

        else -> {
            fileViewModel.closeDialog()
        }
    }

}
