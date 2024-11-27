package github.zerorooot.nap511.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import github.zerorooot.nap511.R
import github.zerorooot.nap511.activity.VideoActivity
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.OrderEnum
import github.zerorooot.nap511.screenitem.FileCellItem
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.concurrent.thread
import kotlin.math.log

@SuppressLint(
    "UnusedMaterial3ScaffoldPaddingParameter",
    "MutableCollectionMutableState",
    "UnrememberedMutableState"
)
@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class
)
@Composable
fun FileScreen(
    fileViewModel: FileViewModel,
    offlineFileViewModel: OfflineFileViewModel,
    appBarOnClick: (String) -> Unit
) {
    val fileBeanList = fileViewModel.fileBeanList
    val path by fileViewModel.currentPath.collectAsState()

    if (!fileViewModel.isFileScreenListState()) {
        fileViewModel.fileScreenListState = rememberLazyListState()
    }

    val listState = fileViewModel.fileScreenListState
    val refreshing by fileViewModel.isRefreshing.collectAsState()

    val activity = LocalContext.current as Activity

    CreateDialogs(fileViewModel, offlineFileViewModel)


    val itemOnLongClick = { i: Int ->
        fileViewModel.isLongClickState = !fileViewModel.isLongClickState
        if (fileViewModel.isLongClickState) {
            fileViewModel.select(i)
        } else {
            fileViewModel.appBarTitle = "nap511"
        }
    }
    val floatingActionButtonOnClick = { i: String ->
        when (i) {
            "CutFloatingActionButton" -> fileViewModel.removeFile()
            //打开添加文件夹
            "AddFloatingActionButton" -> fileViewModel.isOpenCreateFolderDialog = true
            "CloseFloatingActionButton" -> fileViewModel.cancelCut()
        }
    }

    val menuOnClick = { itemName: String, index: Int ->
        when (itemName) {
            "剪切" -> fileViewModel.cut(index)
            "删除" -> fileViewModel.delete(index)
            "重命名" -> {
                fileViewModel.selectIndex = index
                fileViewModel.isOpenRenameFileDialog = true
            }

            "文件信息" -> {
                fileViewModel.selectIndex = index
                fileViewModel.getFileInfo(index)
//                fileViewModel.isOpenFileInfoDialog = true
            }

            "通过aria2下载" -> {
//                val aria2Url = SharedPreferencesUtil(activity).get(ConfigUtil.aria2Url)
                val aria2Url = DataStoreUtil.getData(ConfigUtil.aria2Url, "")
                if (aria2Url == "") {
                    fileViewModel.isOpenAria2Dialog = true
                } else {
                    fileViewModel.startSendAria2Service(index)
                }
            }
        }
    }

    val myItemOnClick = { i: Int ->
        if (fileViewModel.isLongClickState) {
            fileViewModel.select(i)
        } else {
//            //记录上级目录当前的位置
            fileViewModel.setListLocationAndClickCache(i)
            //进入下一级
            val fileBean = fileBeanList[i]
            if (fileBean.isFolder) {
                //提前加载上下两个文件夹
                if (DataStoreUtil.getData(ConfigUtil.earlyLoading, false)) {
                    val before = i - 1
                    val after = i + 1
                    if (before >= 0 && fileBeanList[before].isFolder) {
                        fileViewModel.updateFileCache(fileBeanList[before].categoryId)
                    }
                    if (after < fileBeanList.size && fileBeanList[after].isFolder) {
                        fileViewModel.updateFileCache(fileBeanList[after].categoryId)
                    }
//                    try {
//                        println("fileBean ${fileBean.name} before ${fileBeanList[before].name}  after ${fileBeanList[after].name} ")
//                    } catch (_: Exception) {
//                    }
                }
                //加载文件
                fileViewModel.getFiles(fileBean.categoryId)
            }
            //打开视频
            if (fileBean.isVideo == 1) {
                val intent = Intent(activity, VideoActivity::class.java)
                intent.putExtra("cookie", App.cookie)
                intent.putExtra("title", fileBean.name)
                intent.putExtra("pick_code", fileBean.pickCode)
                activity.startActivity(intent)
            }
            //打开图片
            if (fileBean.photoThumb != "") {
                //具体实现在MainActivity#MyNavigationDrawer()
                val photoFileBeanList =
                    fileViewModel.fileBeanList.filter { ia -> ia.photoThumb != "" }
                fileViewModel.photoFileBeanList.clear()
                fileViewModel.photoFileBeanList.addAll(photoFileBeanList)
                fileViewModel.photoIndexOf = photoFileBeanList.indexOf(fileBean)
                App.selectedItem = "photo"
            }
            // 打开种子文件
            if (fileBean.fileIco == R.drawable.torrent) {
                offlineFileViewModel.isOpenCreateSelectTorrentFileDialog = true
                offlineFileViewModel.getTorrentTask(fileBean.sha1)
            }


            //滚动到当前目录
            if (fileBean.isFolder) {
                fileViewModel.getListLocation(
                    path + "/${fileBean.name}"
                )
            }
        }
    }
    val onBack = {
        val lastIndexOf = path.lastIndexOf("/")
        val parentDirectory = path.subSequence(
            0, if (lastIndexOf == -1) {
                0
            } else {
                lastIndexOf
            }
        ).toString()
        //appBar 也调用了这个，所以再判断一次
        if (path != "/根目录" && !fileViewModel.isLongClickState) {
            //当前目录的位置
            fileViewModel.setListLocation(path)
            fileViewModel.getListLocation(parentDirectory)
        }
        fileViewModel.back()
    }
    BackHandler(
        path != "/根目录" || fileViewModel.isLongClickState || fileViewModel.isSearchState,
        onBack
    )

    val myAppBarOnClick = { i: String ->
        if (i == "back") {
            if (path == "/根目录" && !fileViewModel.isSearchState) {
                App.instance.openDrawerState()
            } else {
                onBack()
            }
        }
        appBarOnClick.invoke(i)
    }

    val pullRefreshState = rememberPullRefreshState(refreshing, { fileViewModel.refresh() })

    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    Column {
        AnimatedContent(targetState = fileViewModel.isLongClickState, transitionSpec = {
            fadeIn() togetherWith fadeOut()
        }, label = "") {
            if (it) {
                AppTopBarMultiple(fileViewModel.appBarTitle, myAppBarOnClick)
            } else {
                AppTopBarNormal(fileViewModel.appBarTitle, myAppBarOnClick)
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        clipboardManager.setText(AnnotatedString((path)))
                        App.instance.toast("$path 已复制到剪切板")
                    },
                    onDoubleClick = {
                        //滚到顶端
                        fileViewModel.getListLocation("null")
                    },
                    onLongClick = {
                        clipboardManager.setText(AnnotatedString((fileViewModel.currentCid)))
                        App.instance.toast("cid ${fileViewModel.currentCid} 已复制到剪切板")
                    },
                ),
        ) {
            MiddleEllipsisText(
                text = path, modifier = Modifier
                    .padding(8.dp, 4.dp)
            )
        }

        Scaffold(floatingActionButton = {
            AnimatedContent(targetState = fileViewModel.isCutState, transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }, label = "") {
                if (it) {
                    Column() {
                        FloatingActionButton(onClick = {
                            floatingActionButtonOnClick.invoke("CloseFloatingActionButton")
                        }) {
                            Icon(Icons.Filled.Close, "close")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        FloatingActionButton(onClick = {
                            floatingActionButtonOnClick.invoke("CutFloatingActionButton")
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_content_paste_24),
                                "cut"
                            )
                        }
                    }
                } else {
                    FloatingActionButton(onClick = {
                        floatingActionButtonOnClick.invoke("AddFloatingActionButton")
                    }) {
                        Icon(Icons.Filled.Add, "add")
                    }
                }
            }
        }) {
            Box(Modifier.pullRefresh(pullRefreshState)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(), state = listState
                ) {
                    itemsIndexed(items = fileBeanList, key = { _, item ->
                        item.hashCode()
                    }) { index, item ->
                        FileCellItem(
                            item,
                            index,
                            fileViewModel.clickMap.getOrDefault(path, -1),
                            Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                            myItemOnClick,
                            itemOnLongClick,
                            menuOnClick
                        )
                    }
                }
                PullRefreshIndicator(
                    refreshing, pullRefreshState, Modifier.align(Alignment.TopCenter)
                )
            }

        }
    }
}

@ExperimentalMaterial3Api
@Composable
fun CreateDialogs(fileViewModel: FileViewModel, offlineFileViewModel: OfflineFileViewModel) {
    val context = LocalContext.current
    //新建文件夹
    CreateFolderDialog(fileViewModel) {
        if (it != "") {
            fileViewModel.createFolder(it)
        }
        fileViewModel.isOpenCreateFolderDialog = false
    }
    //重命名
    RenameFileDialog(fileViewModel) {
        if (it != "") {
            fileViewModel.rename(it)
        }
        fileViewModel.isOpenRenameFileDialog = false
    }
    //文件信息
    FileInfoDialog(fileViewModel) {
        fileViewModel.isOpenFileInfoDialog = false
    }
    //文件排序
    FileOrderDialog(fileViewModel = fileViewModel) {
        fileViewModel.isOpenFileOrderDialog = false
        if (it.contains("视频时间")) {
            fileViewModel.fileBeanList.sortByDescending { fileBean -> fileBean.playLong }
            //滚动到顶部
            fileViewModel.getListLocation("null")
            return@FileOrderDialog
        }
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
    Aria2Dialog(
        fileViewModel = fileViewModel, context = DataStoreUtil.getData(
            ConfigUtil.aria2Url, ConfigUtil.aria2UrldefValue
        )
    ) {
        fileViewModel.isOpenAria2Dialog = false
        if (it != "") {
            val jsonObject = JsonParser().parse(it).asJsonObject
            val aria2Url = jsonObject.get(ConfigUtil.aria2Url).asString
            val aria2Token = jsonObject.get(ConfigUtil.aria2Token).asString
            thread { checkAria2(aria2Url, aria2Token, context) }
        }
    }
//搜索
    SearchDialog(fileViewModel) {
        if (it != "") {
            fileViewModel.search(it)
        }
        fileViewModel.isOpenSearchDialog = false
    }
    CreateSelectTorrentFileDialog(offlineFileViewModel) { torrentFileBean, map ->
        offlineFileViewModel.isOpenCreateSelectTorrentFileDialog = false
        if (map.isEmpty()) {
            return@CreateSelectTorrentFileDialog
        }
        offlineFileViewModel.addTorrentTask(torrentFileBean, map.keys.joinToString(separator = ","))
    }

}

/**
 * {"jsonrpc":"2.0","id":"nap511","method":"aria2.getVersion","params":["token:11"]}
 */
private fun checkAria2(aria2Url: String, aria2Token: String, context: Context) {
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
        val bodyJson = JsonParser().parse(body).asJsonObject

        if (bodyJson.has("error")) {
            "aria2配置失败," + bodyJson.getAsJsonObject("error").get("message").asString
        } else {
            DataStoreUtil.putData(ConfigUtil.aria2Url, aria2Url)
            DataStoreUtil.putData(ConfigUtil.aria2Token, aria2Token)
            "aria2配置成功，请重新下载文件"
        }

    } catch (e: Exception) {
        "aria2配置失败," + e.message.toString()
    }
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
