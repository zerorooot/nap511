package github.zerorooot.nap511.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.R
import github.zerorooot.nap511.activity.VideoActivity
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.OrderEnum
import github.zerorooot.nap511.viewmodel.FileViewModel
import kotlinx.coroutines.launch

@SuppressLint(
    "UnusedMaterial3ScaffoldPaddingParameter",
    "MutableCollectionMutableState",
    "UnrememberedMutableState"
)
@OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class
)
@Composable
fun FileScreen(
    fileViewModel: FileViewModel,
    appBarOnClick: (String) -> Unit
) {
    val fileBeanList = fileViewModel.fileBeanList
    val path by fileViewModel.currentPath.collectAsState()

    if (!fileViewModel.isFileScreenListState()) {
        fileViewModel.fileScreenListState = rememberLazyListState()
    }

    val listState = fileViewModel.fileScreenListState
    val coroutineScope = rememberCoroutineScope()
    val refreshing by fileViewModel.isRefreshing.collectAsState()

    val activity = LocalContext.current as Activity

    CreateDialogs(fileViewModel)

    val itemOnLongClick = { i: Int ->
        fileViewModel.isLongClick = !fileViewModel.isLongClick
        if (fileViewModel.isLongClick) {
            fileViewModel.select(i)
        } else {
            fileViewModel.appBarTitle = "nap511"
        }
    }
    val floatingActionButtonOnClick = { i: String ->
        when (i) {
            "CutFloatingActionButton" -> fileViewModel.removeFile()
            "AddFloatingActionButton" -> fileViewModel.isOpenCreateFolderDialog = true
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
                fileViewModel.isOpenFileInfoDialog = true
            }
        }
    }

    val myItemOnClick = { i: Int ->
        if (fileViewModel.isLongClick) {
            fileViewModel.select(i)
        } else {
//            //记录上级目录当前的位置
            fileViewModel.setListLocationAndClickCache(i)

            val currentFileBean = fileBeanList[i]
            val currentPath = path

            //进入下一级
            val fileBean = fileViewModel.fileBeanList[i]
            if (fileBean.isFolder) {
                fileViewModel.getFiles(fileBean.categoryId)
            }
            //todo click to remember
            if (fileBean.isVideo == 1) {
                val intent = Intent(activity, VideoActivity::class.java)
                intent.putExtra("cookie", fileViewModel.myCookie)
                intent.putExtra("title", fileBean.name)
                intent.putExtra("pick_code", fileBean.pickCode)
                activity.startActivity(intent)
            }
            if (fileBean.photoThumb != "") {
                //具体实现在MainActivity#MyNavigationDrawer()
                val photoFileBeanList =
                    fileViewModel.fileBeanList.filter { ia -> ia.photoThumb != "" }
                fileViewModel.photoFileBeanList.clear()
                fileViewModel.photoFileBeanList.addAll(photoFileBeanList)
                fileViewModel.photoIndexOf = photoFileBeanList.indexOf(fileBean)
                fileViewModel.selectedItem = "photo"
            }


            //滚动到当前目录
            if (currentFileBean.isFolder) {
                coroutineScope.launch {
                    fileViewModel.getListLocation(currentPath + "/${currentFileBean.name}")
                }
            }
        }
    }
    val onBack = {
        val lastIndexOf = path.lastIndexOf("/")
        val parentDirectory = path.subSequence(
            0,
            if (lastIndexOf == -1) {
                0
            } else {
                lastIndexOf
            }
        ).toString()
        //appBar 也调用了这个，所以再判断一次
        if (path != "/根目录" && !fileViewModel.isCut && !fileViewModel.isLongClick) {
            //当前目录的位置
            fileViewModel.setListLocation(path)
            coroutineScope.launch {
                fileViewModel.getListLocation(parentDirectory)
            }
        }
        fileViewModel.back()
    }
    BackHandler(path != "/根目录" || fileViewModel.isCut || fileViewModel.isLongClick, onBack)


    val myAppBarOnClick = { i: String ->
        if (i == "back") {
            onBack()
        }
        appBarOnClick.invoke(i)
    }

    val pullRefreshState = rememberPullRefreshState(refreshing, { fileViewModel.refresh() })

    Column {
        AnimatedContent(targetState = fileViewModel.isLongClick, transitionSpec = {
            fadeIn() with fadeOut()
        }) {
            if (it) {
                AppTopBarMultiple(fileViewModel.appBarTitle, myAppBarOnClick)
            } else {
                AppTopBarNormal(fileViewModel.appBarTitle, myAppBarOnClick)
            }
        }
        MiddleEllipsisText(
            text = path, modifier = Modifier.padding(8.dp, 4.dp)
        )
        Scaffold(floatingActionButton = {
            AnimatedContent(targetState = fileViewModel.isCut, transitionSpec = {
                fadeIn() with fadeOut()
            }) {
                if (it) {
                    FloatingActionButton(onClick = {
                        floatingActionButtonOnClick.invoke("CutFloatingActionButton")
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_content_paste_24),
                            "cut"
                        )
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
                    modifier = Modifier.fillMaxSize(),
                    state = listState
                ) {
                    itemsIndexed(items = fileBeanList, key = { _, item ->
                        item.hashCode()
                    }) { index, item ->
                        FileCellItem(
                            item,
                            index,
                            fileViewModel.clickMap.getOrDefault(path, -1),
                            Modifier.animateItemPlacement(),
                            myItemOnClick,
                            itemOnLongClick,
                            menuOnClick
                        )
                    }
                }
                PullRefreshIndicator(
                    refreshing,
                    pullRefreshState,
                    Modifier.align(Alignment.TopCenter)
                )
            }


//            SwipeRefresh(state = rememberSwipeRefreshState(refreshing), onRefresh = {
//                fileViewModel.refresh()
//            }) {
//                LazyColumn(
//                    modifier = Modifier.fillMaxSize(),
//                    state = listState
//                ) {
//                    itemsIndexed(items = fileBeanList, key = { _, item ->
//                        item.hashCode()
//                    }) { index, item ->
//                        FileCellItem(
//                            item,
//                            index,
//                            fileViewModel.clickMap.getOrDefault(path, -1),
//                            Modifier.animateItemPlacement(),
//                            myItemOnClick,
//                            itemOnLongClick,
//                            menuOnClick
//                        )
//                    }
//                }
//            }

        }
    }
}

@ExperimentalMaterial3Api
@Composable
fun CreateDialogs(fileViewModel: FileViewModel) {
    CreateFolderDialog(fileViewModel) {
        if (it != "") {
            fileViewModel.createFolder(it)
        }
        fileViewModel.isOpenCreateFolderDialog = false
    }
    RenameFileDialog(fileViewModel) {
        if (it != "") {
            fileViewModel.rename(it)
        }
        fileViewModel.isOpenRenameFileDialog = false
    }
    FileInfoDialog(fileViewModel) {
        fileViewModel.isOpenFileInfoDialog = false
    }
    FileOrderDialog(fileViewModel = fileViewModel) {
        fileViewModel.isOpenFileOrderDialog = false
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
