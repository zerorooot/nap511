package github.zerorooot.nap511.dialog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.screenitem.AutoSizableTextField
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.closeUnzipAllFileDialog
import github.zerorooot.nap511.viewmodel.closeUnzipDialog
import github.zerorooot.nap511.viewmodel.getZipListFile
import github.zerorooot.nap511.viewmodel.unzipFile

@Composable
fun UnzipPassword(fileBean: FileBean, enter: (String?) -> Unit) {
    BaseDialog(
        title = "云解压-${fileBean.name}",
        label = "请输入密码",
        dismissButtonText = "取消",
        enter = enter
    )
}

@Composable
fun UnzipDialog(fileViewModel: FileViewModel) {
    val fileBean = fileViewModel.fileBeanList.getOrNull(fileViewModel.selectIndex)
    val zipBeanList by fileViewModel.unzipBeanList
    LaunchedEffect(Unit) {
        fileViewModel.setRefreshingStatus(false)
    }
    UnzipScreen(zipBeanList, fileBean?.name ?: "解压文件") {
        if (it.first) {
            when (it.second) {
                "exit" -> {
                    fileViewModel.closeUnzipDialog()
                }

                "up" -> {
                    val path = zipBeanList.pathString.split("/")
                    var fileName = ""
                    var paths = ""
                    try {
                        fileName = path[path.size - 2]
                        paths = path.subList(0, path.size - 2).joinToString(separator = "/")
                    } catch (_: Exception) {
                    }
                    fileViewModel.getZipListFile(fileName, paths)
                }

                "unzipAll" -> {
                    fileViewModel.unzipFile()
                    fileViewModel.closeUnzipDialog()
                }
            }
        } else {
            fileViewModel.getZipListFile(
                it.second, paths = zipBeanList.pathString
            )
        }
    }
}

@Composable
fun UnzipAllFile(
    fileViewModel: FileViewModel
) {
    BaseDialog("请输入解压密码", "如无加密，为空即可") { pwd ->
        if (pwd == null) {
            fileViewModel.closeUnzipAllFileDialog()
            return@BaseDialog
        }

        val currentCid = fileViewModel.currentCid

        fileViewModel.closeUnzipAllFileDialog()

        val message =
            fileViewModel.fileBeanList.filter { i -> i.isSelect && i.fileIco == R.drawable.zip }
                .takeIf { it.isNotEmpty() }?.let {
                    fileViewModel.unzipFile(it, currentCid, pwd)
                    "后台解压中......"
                } ?: run {
                "请选中压缩包解压！"
            }
        App.instance.toast(message)

        fileViewModel.recoverFromLongPress()
        fileViewModel.unSelect()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnzipScreen(
    zipBeanList: ZipBeanList, fileName: String, enter: (Pair<Boolean, String>) -> Unit
) {
    AlertDialog(onDismissRequest = {
        enter.invoke(Pair(true, "exit"))
    }, confirmButton = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.offset(y = (-20).dp)
        ) {
            TextButton(
                onClick = {
                    enter.invoke(Pair(true, "exit"))
                },
            ) {
                Text(text = "关闭")
            }
            TextButton(
                onClick = {
                    enter.invoke(Pair(true, "up"))
                },
            ) {
                Text(text = "上一级")
            }
            TextButton(onClick = {
                enter.invoke(Pair(true, "unzipAll"))
            }) {
                Text(text = "解压到当前文件夹")
            }
        }
    }, title = { Text(text = "云解压-${fileName}") }, text = {
        Column {
            AutoSizableTextField(
                value = zipBeanList.pathString, minFontSize = 30.sp, maxLines = 2
            )

            LazyColumn {
                itemsIndexed(items = zipBeanList.list, key = { _, item ->
                    item.hashCode()
                }) { index, item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxSize()
                            .combinedClickable(onClick = {
                                if (item.fileIco == R.drawable.folder) {
                                    enter.invoke(Pair(false, item.fileName))
                                }
                            })
                    ) {
                        Image(
                            painter = painterResource(item.fileIco),
                            modifier = Modifier
                                .height(30.dp)
                                .width(30.dp),
                            contentScale = ContentScale.Fit,
                            contentDescription = "",
                        )
                        if (item.fileIco == R.drawable.folder) {
                            Text(
                                text = item.fileName,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = item.fileName,
                                )
                                Row {
                                    Text(
                                        text = item.sizeString,
                                    )
                                    Text(
                                        text = item.timeString,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    })
}
