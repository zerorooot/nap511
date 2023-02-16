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
import github.zerorooot.nap511.viewmodel.FileViewModel
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class
)
@Composable
fun FileScreen(
    fileViewModel: FileViewModel,
    itemOnClick: (Int) -> Unit,
    appBarOnClick: (String) -> Unit
) {
    val fileBeanList = fileViewModel.fileBeanList
    val path by fileViewModel.currentPath.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val refreshing by fileViewModel.isRefreshing.collectAsState()


    CreateDialogs(fileViewModel)

    val itemOnLongClick = { i: Int ->
        fileViewModel.isLongClick = !fileViewModel.isLongClick
        if (fileViewModel.isLongClick) {
            fileViewModel.select(i)
        }
    }
    val floatingActionButtonOnClick = { i: String ->
        when (i) {
            "CutFloatingActionButton" -> fileViewModel.removeFile()
            "AddFloatingActionButton" -> fileViewModel.isOpenCreateFolder = true
        }
    }

    val menuOnClick = { itemName: String, index: Int ->
        when (itemName) {
            "剪切" -> fileViewModel.cut(index)
            "删除" -> fileViewModel.delete(index)
            "重命名" -> {
                fileViewModel.selectIndex = index
                fileViewModel.isRenameFile = true
            }
            "文件信息" -> {
                fileViewModel.selectIndex = index
                fileViewModel.isFileInfo = true
            }
        }
    }

    val myItemOnClick = { i: Int ->
        if (fileViewModel.isLongClick) {
            fileViewModel.select(i)
        } else {
            //记录上级目录当前的位置
            val locationBean = LocationBean(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
            fileViewModel.currentLocation[path] = locationBean
            val currentFileBean = fileBeanList[i]
            val currentPath = path


            //进入下一级
            itemOnClick.invoke(i)

            //滚动到当前目录
            if (currentFileBean.isFolder) {
                coroutineScope.launch {
                    val locationBean1 =
                        fileViewModel.currentLocation[currentPath + "/${currentFileBean.name}"]
                    if (locationBean1 != null) {
                        listState.animateScrollToItem(
                            locationBean1.firstVisibleItemIndex,
                            locationBean1.firstVisibleItemScrollOffset
                        )
                    }

                }
            }
        }
    }
    val onBack = {
        if (path != "/根目录") {
            //当前目录的位置
            fileViewModel.currentLocation[path] = LocationBean(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
            val locationBean =
                fileViewModel.currentLocation[path.subSequence(0, path.lastIndexOf("/"))]!!

            coroutineScope.launch {
                listState.animateScrollToItem(
                    locationBean.firstVisibleItemIndex,
                    locationBean.firstVisibleItemScrollOffset
                )
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




    Column {
        AnimatedContent(targetState = fileViewModel.isLongClick, transitionSpec = {
            fadeIn() with fadeOut()
        }) {
            if (it) {
                AppTopBarMultiple(myAppBarOnClick)
            } else {
                AppTopBarNormal(myAppBarOnClick)
            }
        }
        MiddleEllipsisText(
            text = path,
            modifier = Modifier
                .padding(8.dp, 4.dp)
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
            SwipeRefresh(
                state = rememberSwipeRefreshState(refreshing),
                onRefresh = {
                    fileViewModel.refresh()
                }) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState
                ) {
                    itemsIndexed(
                        items = fileBeanList,
                        key = { _, item ->
                            item.hashCode()
                        }
                    ) { index, item ->
                        FileCellItem(
                            item,
                            index,
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

@Composable
fun CreateDialogs(fileViewModel: FileViewModel) {
    CreateFolderDialog(fileViewModel) {
        if (it != "") {
            fileViewModel.createFolder(it)
        }
        fileViewModel.isOpenCreateFolder = false
    }
    RenameFileDialog(fileViewModel) {
        if (it != "") {
            fileViewModel.rename(it)
        }
        fileViewModel.isRenameFile = false
    }
    FileInfoDialog(fileViewModel) {
        fileViewModel.isFileInfo = false
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
    fileBeanList.add(FileBean(name = "1", sizeString = "1", createTimeString = "1"))
    fileBeanList.add(FileBean(name = "2", sizeString = "2", createTimeString = "2"))
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
        AppTopBarNormal {}
        MiddleEllipsisText(
            text = "path",
            modifier = Modifier
                .padding(8.dp, 4.dp)
        )
        Scaffold(floatingActionButton = {
            FloatingActionButton(onClick = {
            }) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_content_paste_24),
                    "cut"
                )
            }

        }) {
            SwipeRefresh(
                state = rememberSwipeRefreshState(false),
                onRefresh = {

                }) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState
                ) {
                    itemsIndexed(
                        items = fileBeanList,
                        key = { _, item ->
                            item.hashCode()
                        }
                    ) { index, item ->
                        FileCellItem(
                            item,
                            index,
                            Modifier.animateItemPlacement(),
                            {},
                            {},
                            { _, _ -> }
                        )
                    }
                }
            }

        }
    }
}






