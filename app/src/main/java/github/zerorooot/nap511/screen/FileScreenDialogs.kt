package github.zerorooot.nap511.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.OrderEnum
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
import github.zerorooot.nap511.viewmodel.createFolder
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
    fileViewModel: FileViewModel = viewModel<FileViewModel>(),
    offlineFileViewModel: OfflineFileViewModel = viewModel<OfflineFileViewModel>()
) {
//    val context = LocalContext.current
    //新建文件夹
    CreateFolderDialog(fileViewModel) {
        if (it != null && it != "") {
            fileViewModel.createFolder(it)
        }
        fileViewModel.closeCreateFolderDialog()
    }
    //重命名
    RenameFileDialog(fileViewModel) {
        if (it != null && it != "") {
            fileViewModel.rename(it)
        }
        fileViewModel.closeRenameFileDialog()
    }
    //文件信息
    FileInfoDialog(fileViewModel, { fileViewModel.closeFileInfoDialog() }) {
        if (fileViewModel.isSearchState) {
            fileViewModel.isSearchState = false
            fileViewModel.recoverFromLongPress()
        }
        fileViewModel.getFiles(it)
        fileViewModel.closeFileInfoDialog()
    }
    //文件排序
    FileOrderDialog(fileViewModel) {
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
//aria2
    val scope = rememberCoroutineScope()
    Aria2Dialog(
        fileViewModel,
        context = DataStoreUtil.getData(
            ConfigKeyUtil.ARIA2_URL, ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE
        )
    ) {
        fileViewModel.closeAria2Dialog()
        if (it != "") {
            val jsonObject = Gson().fromJson(it, JsonObject::class.java)
            val aria2Url = jsonObject.get(ConfigKeyUtil.ARIA2_URL).asString
            val aria2Token = jsonObject.get(ConfigKeyUtil.ARIA2_TOKEN).asString
            scope.launch(Dispatchers.IO) { checkAria2(aria2Url, aria2Token) }
        }
    }
    //搜索
    SearchDialog(fileViewModel) {
        if (it != null && it != "") {
            fileViewModel.search(it)
        }
        fileViewModel.closeSearchDialog()
    }

    CreateSelectTorrentFileDialog(
        fileViewModel,
        offlineFileViewModel
    ) { infoHash, savePath, wanted ->
        fileViewModel.closeCreateSelectTorrentFileDialog()
        if (wanted.isEmpty()) {
            return@CreateSelectTorrentFileDialog
        }
        offlineFileViewModel.addTorrentTask(
            infoHash, savePath, wanted
        ) {
            if (it) {
                fileViewModel.selectedItem = ConfigKeyUtil.VERIFY_MAGNET_LINK_ACCOUNT
            }
        }
    }
    //解压文件
    UnzipDialog(fileViewModel)
    //小文本文件
    TextBodyDialog(fileViewModel)
    //
    UnzipAllFile(fileViewModel)

}

/**
 * {"jsonrpc":"2.0","id":"nap511","method":"aria2.getVersion","params":["token:11"]}
 */
private fun checkAria2(aria2Url: String, aria2Token: String) {
    val okHttpClient = OkHttpClient()
    val jsonObject = JsonObject()
    jsonObject.addProperty("jsonrpc", "2.0")
    jsonObject.addProperty("id", "nap511")
    jsonObject.addProperty("method", "aria2.getVersion")

    val jsonArray = JsonArray()
    if (aria2Token != "") {
        jsonArray.add("token:$aria2Token")
    }
    jsonObject.add("params", jsonArray)

    val request: Request = Request.Builder().url(aria2Url).post(
        jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
    ).build()

    val message: String = try {
        val body = okHttpClient.newCall(request).execute().body.string()
        val bodyJson = Gson().fromJson(body, JsonObject::class.java)


        if (bodyJson.has("error")) {
            "aria2配置失败," + bodyJson.getAsJsonObject("error").get("message").asString
        } else {
            DataStoreUtil.putData(ConfigKeyUtil.ARIA2_URL, aria2Url)
            DataStoreUtil.putData(ConfigKeyUtil.ARIA2_TOKEN, aria2Token)
            "aria2配置成功，请重新下载文件"
        }

    } catch (e: Exception) {
        "aria2配置失败," + e.message.toString()
    }
    App.instance.toast(message)
}
