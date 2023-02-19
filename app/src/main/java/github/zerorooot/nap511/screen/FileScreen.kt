package github.zerorooot.nap511.screen

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.LocationBean
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
    ExperimentalMaterial3Api::class
)
@Composable
fun FileScreen(
    fileViewModel: FileViewModel, itemOnClick: (Int) -> Unit, appBarOnClick: (String) -> Unit
) {
    val fileBeanList = fileViewModel.fileBeanList
    val path by fileViewModel.currentPath.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val refreshing by fileViewModel.isRefreshing.collectAsState()
    val clickMap by remember {
        mutableStateOf(hashMapOf<String, Int>())
    }
    var clickIndex by mutableStateOf(-1)


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
            //记录上级目录当前的位置
            val locationBean = LocationBean(
                listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset
            )
            fileViewModel.currentLocation[path] = locationBean
            val currentFileBean = fileBeanList[i]
            val currentPath = path

            //标记此点击文件，方便确认到底点了那个
            clickMap[currentPath] = i
            //currentPath只是当前的，点击后进入下一个文件夹。故仅记录文件
            if (!currentFileBean.isFolder) {
                clickIndex = i
            }

            //进入下一级
            itemOnClick.invoke(i)

            //滚动到当前目录
            if (currentFileBean.isFolder) {
                coroutineScope.launch {
                    val locationBean1 =
                        fileViewModel.currentLocation[currentPath + "/${currentFileBean.name}"]
                            ?: run {
                                LocationBean(0, 0)
                            }
                    listState.scrollToItem(
                        locationBean1.firstVisibleItemIndex,
                        locationBean1.firstVisibleItemScrollOffset
                    )


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
            fileViewModel.currentLocation[path] = LocationBean(
                listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset
            )
            val locationBean =
                fileViewModel.currentLocation[parentDirectory]!!

            coroutineScope.launch {
                listState.scrollToItem(
                    locationBean.firstVisibleItemIndex, locationBean.firstVisibleItemScrollOffset
                )
            }
        }
        clickIndex = clickMap[parentDirectory] ?: run { -1 }
        fileViewModel.back()
    }
    BackHandler(path != "/根目录" || fileViewModel.isCut || fileViewModel.isLongClick, onBack)


    val myAppBarOnClick = { i: String ->
        if (i == "back") {
            onBack()
        }
        appBarOnClick.invoke(i)
    }


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
            SwipeRefresh(state = rememberSwipeRefreshState(refreshing), onRefresh = {
                fileViewModel.refresh()
            }) {
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
                            clickIndex,
                            clickMap.getOrDefault(path, -1),
                            Modifier.animateItemPlacement(),
                            myItemOnClick,
                            itemOnLongClick,
                            menuOnClick
                        )
                    }
                }
            }

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
            val asc = if (it.subSequence(it.length - 1, it.length) == "↑") 1 else 0
            val type = when (it.subSequence(0, it.length - 1)) {
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

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(

    ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class
)
@Preview
@Composable
fun te() {
    val fileBeanList = arrayListOf<FileBean>()
    fileBeanList.add(
        FileBean(
            name = "1dsfsadfasdfasdfsafsdfasdfdasfasfsafsasfdasfsdfasfasdfsdafsdafsafsdarew",
            sizeString = "1",
            createTimeString = "1"
        )
    )
    fileBeanList.add(
        FileBean(
            name = "2212321sdaffsas312233", sizeString = "2", createTimeString = "2"
        )
    )
    fileBeanList.add(FileBean(name = "3", sizeString = "3", createTimeString = "3"))
    fileBeanList.add(FileBean(name = "4", sizeString = "4", createTimeString = "4"))
    fileBeanList.add(FileBean(name = "5", sizeString = "5", createTimeString = "5"))
    fileBeanList.add(FileBean(name = "41", sizeString = "4", createTimeString = "4"))
    fileBeanList.add(FileBean(name = "42", sizeString = "4", createTimeString = "4"))
    fileBeanList.add(FileBean(name = "43", sizeString = "4", createTimeString = "4"))
    fileBeanList.add(FileBean(name = "44", sizeString = "4", createTimeString = "4"))
    fileBeanList.add(FileBean(name = "45", sizeString = "4", createTimeString = "4"))
    fileBeanList.add(FileBean(name = "46", sizeString = "4", createTimeString = "4"))
    fileBeanList.add(FileBean(name = "47", sizeString = "4", createTimeString = "4"))
    fileBeanList.add(FileBean(name = "48", sizeString = "4", createTimeString = "4"))
    fileBeanList.add(FileBean(name = "49", sizeString = "4", createTimeString = "4"))
    val listState = rememberLazyListState()
    Column {
        AppTopBarNormal("") {}
        MiddleEllipsisText(
            text = "path", modifier = Modifier.padding(8.dp, 4.dp)
        )
        Scaffold(floatingActionButton = {
            FloatingActionButton(onClick = {}) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_content_paste_24), "cut"
                )
            }

        }) {
            SwipeRefresh(state = rememberSwipeRefreshState(false), onRefresh = {

            }) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(), state = listState
                ) {
                    itemsIndexed(items = fileBeanList, key = { _, item ->
                        item.hashCode()
                    }) { index, item ->
                        FileCellItem(item,
                            index, -1, -1,
                            Modifier.animateItemPlacement(),
                            {},
                            {},
                            { _, _ -> })
                    }
                }
            }

        }
    }
}






