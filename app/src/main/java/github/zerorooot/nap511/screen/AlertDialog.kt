package github.zerorooot.nap511.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.bean.TorrentFileListWeb
import github.zerorooot.nap511.screenitem.AutoSizableTextField
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import kotlinx.coroutines.delay
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import kotlin.system.exitProcess

@Composable
fun CreateFolderDialog(fileViewModel: FileViewModel, enter: (String) -> Unit) {
    if (fileViewModel.isOpenCreateFolderDialog) {
        BaseDialog("请输入新建文件名", "文件名", enter = enter)
    }
}

@Composable
fun SearchDialog(fileViewModel: FileViewModel, enter: (String) -> Unit) {
    if (fileViewModel.isOpenSearchDialog) {
        BaseDialog("在当前目录下搜索", "关键字", enter = enter)
    }
}


@Composable
fun RenameFileDialog(fileViewModel: FileViewModel, enter: (String) -> Unit) {
    if (fileViewModel.isOpenRenameFileDialog) {
        BaseDialog(
            "重命名文件",
            "新文件名",
            fileViewModel.fileBeanList[fileViewModel.selectIndex].name,
            enter = enter
        )
    }
}

@Composable
fun FileInfoDialog(fileViewModel: FileViewModel, enter: (String) -> Unit) {
    if (fileViewModel.isOpenFileInfoDialog) {
        val fileBean = fileViewModel.fileBeanList[fileViewModel.selectIndex]
        BaseDialog(
            title = fileBean.name,
            label = "文件信息",
            readOnly = true,
            context = fileBean.toString() + "\n" + fileViewModel.fileInfo,
            enter = enter
        )
    }
}

@Composable
fun OfflineFileInfoDialog(
    offlineFileViewModel: OfflineFileViewModel, enter: (String) -> Unit
) {
    val isOpenOfflineDialog by offlineFileViewModel.isOpenOfflineDialog.collectAsState()
    if (isOpenOfflineDialog) {
        val offlineTask = offlineFileViewModel.offlineTask
        BaseDialog(
            title = offlineTask.name,
            label = "文件信息",
            readOnly = true,
            context = offlineTask.toString(),
            enter = enter
        )
    }
}

@Composable
fun RecyclePasswordDialog(
    recycleViewModel: RecycleViewModel, enter: (String) -> Unit
) {
    val isOpenPasswordDialog by recycleViewModel.isOpenPasswordDialog.collectAsState()
    if (isOpenPasswordDialog) {
        BaseDialog(
            title = "请输入6位数字安全密钥",
            label = "数字安全密钥",
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            enter = enter
        )
    }
}

@Composable
fun ExitApp() {
    var isOpen by remember {
        mutableStateOf(true)
    }

    if (isOpen) {
        InfoDialog(
            onDismissRequest = {
                isOpen = false
                App.selectedItem = ConfigKeyUtil.MY_FILE
            },
            onConfirmation = {
                android.os.Process.killProcess(android.os.Process.myPid());
                exitProcess(1);
            },
            dialogTitle = "是否离开Nap511?",
        )
    }


}

@Composable
fun CookieDialog(enter: (String) -> Unit) {
    var isOpen by remember {
        mutableStateOf(true)
    }

    if (isOpen) {
        BaseDialog(
            title = "设置Cookie",
            label = "请输入Cookie",
            dismissButtonText = "通过网页登陆"
        ) {
            enter.invoke(it)
            isOpen = false
        }
    }

}

@ExperimentalMaterial3Api
@Composable
fun FileOrderDialog(fileViewModel: FileViewModel, enter: (String) -> Unit) {
    if (fileViewModel.isOpenFileOrderDialog) {
        val fileOrderList = stringArrayResource(id = R.array.fileOrder).toList()
        RadioButtonDialog(fileOrderList, fileViewModel.orderBean.toString(), enter)
    }
}


@Composable
fun Aria2Dialog(fileViewModel: FileViewModel, context: String, enter: (String) -> Unit) {
    if (!fileViewModel.isOpenAria2Dialog) {
        return
    }

    var urlText by remember {
        mutableStateOf(
            TextFieldValue(
                text = context, selection = TextRange(context.length)
            )
        )
    }

    var tokenText by remember {
        mutableStateOf(
            TextFieldValue("")
        )
    }

    val focusRequester = remember { FocusRequester() }

    AlertDialog(onDismissRequest = {
        enter.invoke("")
    }, confirmButton = {
        Button(onClick = {
            val jsonObject = JsonObject()
            jsonObject.addProperty(ConfigKeyUtil.ARIA2_URL, urlText.text)
            jsonObject.addProperty(ConfigKeyUtil.ARIA2_TOKEN, tokenText.text)
            enter.invoke(jsonObject.toString())
            urlText = TextFieldValue("")
            tokenText = TextFieldValue("")
        }) {
            Text(text = "确认")
        }
    }, dismissButton = {
        TextButton(
            onClick = {
                enter.invoke("")
                urlText = TextFieldValue("")
                tokenText = TextFieldValue("")
            },
        ) {
            Text(text = "取消")
        }
    }, title = {
        Text(text = "请配置aria2相关内容", style = MaterialTheme.typography.titleMedium)
    }, text = {
        Column(Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = urlText,
                modifier = Modifier
                    .focusRequester(focusRequester),
                textStyle = LocalTextStyle.current,
                label = { Text(text = "aria2网址") },
                placeholder = { Text(text = "http://x.x.x.x:6800/jsonrpc") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        "clear",
                        modifier = Modifier.clickable(onClick = {
                            urlText = TextFieldValue("")
                        })
                    )
                },
                onValueChange = {
                    urlText = it
                },
            )
            OutlinedTextField(
                value = tokenText,
                textStyle = LocalTextStyle.current,
                label = { Text(text = "aria2秘钥") },
                placeholder = { Text(text = "没配置留空即可") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        "clear",
                        modifier = Modifier.clickable(onClick = {
                            tokenText = TextFieldValue("")
                        })
                    )
                },
                onValueChange = {
                    tokenText = it
                },
            )
        }
    }, shape = MaterialTheme.shapes.medium, properties = DialogProperties(
        //自适应OutlinedTextField高
        usePlatformDefaultWidth = false
    )
    )
    LaunchedEffect(Unit) {
        delay(10)
        focusRequester.requestFocus()
    }
}

@Composable
private fun RadioButtonDialog(
    items: List<String>, selectValue: String = "", enter: (String) -> Unit
) {
    val selectedValue = remember { mutableStateOf(selectValue) }

    val isSelectedItem: (String) -> Boolean = { selectedValue.value == it }
    val onChangeState: (String) -> Unit = { selectedValue.value = it }

    AlertDialog(onDismissRequest = { enter.invoke("") }, confirmButton = {
        TextButton(
            onClick = {
                enter.invoke("")
            },
        ) {
            Text(text = "取消")
        }
    }, title = { Text(text = "选择排序模式") }, text = {
        Column(Modifier.padding(8.dp)) {
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .selectable(
                            selected = isSelectedItem(item), onClick = {
                                onChangeState(item)
                                enter.invoke(item)
                            }, role = Role.RadioButton
                        )
                        .padding(8.dp)
                ) {
                    Icon(
                        modifier = Modifier.padding(end = 16.dp),
                        imageVector = if (isSelectedItem(item)) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.RadioButtonUnchecked
                        },

                        contentDescription = null,
                        tint = Color.Magenta
                    )
                    Text(
                        text = item, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    })

}

@Composable
fun CreateSelectTorrentFileDialog(
    enter: (TorrentFileBean, Map<Int, TorrentFileListWeb>) -> Unit
) {
    val offlineFileViewModel = viewModel<OfflineFileViewModel>()
    if (offlineFileViewModel.isOpenCreateSelectTorrentFileDialog) {
        val torrentBean = offlineFileViewModel.torrentBean
        if (!torrentBean.state) {
            return
        }
        viewModel<FileViewModel>().setRefreshingStatus(false)
        SelectTorrentFileDialog(torrentBean, enter)
    }
}

@Composable
private fun SelectTorrentFileDialog(
    torrentFileBean: TorrentFileBean,
    enter: (torrentFileBean: TorrentFileBean, Map<Int, TorrentFileListWeb>) -> Unit
) {
    val listState = rememberLazyListState()
    //select all
    val selectMap = remember {
        mutableStateMapOf<Int, TorrentFileListWeb>().apply {
            torrentFileBean.torrentFileListWeb.forEachIndexed { index, torrentFileListWeb ->
                if (torrentFileListWeb.wanted == 1) {
                    this[index] = torrentFileListWeb
                }
            }
        }
    }
    val isSelectedItem: (Int) -> Boolean = { selectMap.containsKey(it) }
    val onChangeState: (Int, TorrentFileListWeb) -> Unit = { i: Int, s: TorrentFileListWeb ->
        if (selectMap.containsKey(i)) {
            selectMap.remove(i)
        } else {
            selectMap[i] = s
        }
    }
    AlertDialog(
        onDismissRequest = {
            selectMap.clear()
            enter.invoke(torrentFileBean, selectMap)
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.offset(y = (-20).dp)
            ) {
                TextButton(
                    onClick = {
                        enter.invoke(torrentFileBean, selectMap)
                    },
                ) {
                    Text(text = "下载")
                }
                TextButton(
                    onClick = {
                        selectMap.clear()
                        torrentFileBean.torrentFileListWeb.forEachIndexed { index, torrentFileListWeb ->
                            if (torrentFileListWeb.wanted == 1) {
                                selectMap[index] = torrentFileListWeb
                            }
                        }
                    },
                ) {
                    Text(text = "全选")
                }
                TextButton(
                    onClick = {
                        torrentFileBean.torrentFileListWeb.forEachIndexed { index, torrentFileListWeb ->
                            if (torrentFileListWeb.wanted == 1) {
                                if (selectMap.containsKey(index)) {
                                    selectMap.remove(index)
                                } else {
                                    selectMap[index] = torrentFileListWeb
                                }
                            }
                        }
                    },
                ) {
                    Text(text = "反选")
                }
                TextButton(
                    onClick = {
                        selectMap.clear()
                        enter.invoke(torrentFileBean, selectMap)
                    },
                ) {
                    Text(text = "取消")
                }
            }
        }, title = { Text(text = "选择要下载的文件") }, text = {
            Column() {
                AutoSizableTextField(
                    value = "已经选择${selectMap.size}/${torrentFileBean.fileCount}个，总计：${
                        android.text.format.Formatter.formatFileSize(
                            App.instance,
                            selectMap.values.sumOf { it.size })
                    }\n" + "共${torrentFileBean.fileCount}个文件，总计：${torrentFileBean.fileSizeString}",
                    minFontSize = 30.sp,
                    maxLines = 2
                )
                LazyColumnScrollbar(
                    state = listState,
                    settings = ScrollbarSettings.Default.copy(
                        thumbUnselectedColor = Purple80
                    )
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp), state = listState) {
                        itemsIndexed(items = torrentFileBean.torrentFileListWeb, key = { _, item ->
                            item.hashCode()
                        }) { index, item ->
                            if (item.wanted == 1) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .selectable(
                                            selected = isSelectedItem(index), onClick = {
                                                onChangeState(index, item)
//                                            println("select $selectMap")
                                            }, role = Role.RadioButton
                                        )
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        modifier = Modifier.padding(end = 16.dp),
                                        imageVector = if (isSelectedItem(index)) {
                                            Icons.Outlined.CheckBox
                                        } else {
                                            Icons.Outlined.CheckBoxOutlineBlank
                                        },
                                        contentDescription = null,
                                        tint = Color.Magenta
                                    )
                                    DynamicEllipsizedTextView(
                                        text = item.path,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = item.sizeString, modifier = Modifier.weight(0.5f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        })

}

@Composable
@Preview
fun SelectTorrentFileDialogPreview() {
//    val torrentFileBean = Gson().fromJson(
//        "{\"state\":true,\"errno\":0,\"fileSizeString\":123G,\"errtype\":\"suc\",\"errcode\":0,\"file_size\":70966705837,\"torrent_name\":\"name\",\"file_count\":28,\"info_hash\":\"hash\",\"torrent_filelist_web\":[{\"size\":12312,\"path\":\"预览图/0.JPG\",\"wanted\":1},{\"size\":123443242,\"path\":\"预览图/123123132132131312313123123/1.JPG\",\"wanted\":1},{\"size\":3902418,\"path\":\"预览图/2.JPG\",\"wanted\":1},{\"size\":321231321,\"path\":\"预览图/3.JPG\",\"wanted\":1},{\"size\":312321321,\"path\":\"预览图/4.JPG\",\"wanted\":-1}]}",
//        TorrentFileBean::class.java
//    )
//
//    SelectTorrentFileDialog(torrentFileBean) { a: TorrentFileBean, m: Map<Int, TorrentFileListWeb> ->
//
//    }

}


@Composable
fun DynamicEllipsizedTextView(text: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val textWidth = remember { mutableIntStateOf(0) }

    Box(modifier = modifier.onSizeChanged { textWidth.intValue = it.width }) {
        val maxChars = textWidth.intValue / with(density) { 12.toDp().toPx().toInt() } // 假设每字符占12px
        val halfChars = maxChars / 2
        EllipsizedTextView(text, maxStartChars = halfChars, maxEndChars = halfChars)
    }
}

@Composable
fun EllipsizedTextView(
    text: String,
    maxStartChars: Int = 10, // 开头显示的字符数
    maxEndChars: Int = 10,   // 结尾显示的字符数
    ellipsis: String = "..."
) {
    val displayText = if (text.length > maxStartChars + maxEndChars) {
        text.take(maxStartChars) + ellipsis + text.takeLast(maxEndChars)
    } else {
        text // 如果文本长度小于或等于限制，直接显示
    }

    Text(text = displayText)
}

@Composable
fun InfoDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
) {
    AlertDialog(
        title = {
            Text(text = dialogTitle)
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            Button(onClick = {
                onConfirmation()
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun BaseDialog(
    title: String,
    label: String,
    context: String = "",
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    confirmButtonText: String = "确认",
    dismissButtonText: String = "取消",
    enter: (String) -> Unit,
) {
    var text by remember {
        mutableStateOf(
            TextFieldValue(
                text = context, selection = TextRange(context.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(onDismissRequest = {
        enter.invoke("")
    }, confirmButton = {
        Button(onClick = {
            enter.invoke(text.text)
            text = TextFieldValue("")
        }) {
            Text(text = confirmButtonText)
        }
    }, dismissButton = {
        TextButton(
            onClick = {
                enter.invoke(
                    if (dismissButtonText == "取消") {
                        ""
                    } else {
                        dismissButtonText
                    }
                )
                text = TextFieldValue("")
            },
        ) {
            Text(text = dismissButtonText)
        }
    }, title = {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
    }, text = {
        OutlinedTextField(
            value = text,
            keyboardOptions = keyboardOptions,
            modifier = Modifier
                .focusRequester(focusRequester)
                .heightIn(1.dp, Dp.Infinity),
            readOnly = readOnly,
            textStyle = if (readOnly) LocalTextStyle.current.copy(textAlign = TextAlign.Center) else LocalTextStyle.current,
            label = { Text(text = label) },
            trailingIcon = {
                if (!readOnly) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        "clear",
                        modifier = Modifier.clickable(onClick = {
                            text = TextFieldValue("")
                        })
                    )
                }
            },
            onValueChange = {
                text = it
            },
        )
    }, shape = MaterialTheme.shapes.medium, properties = DialogProperties(
        //自适应OutlinedTextField高
        usePlatformDefaultWidth = false
    )
    )
    LaunchedEffect(Unit) {
        delay(10)
        focusRequester.requestFocus()
    }
}


@Composable
@Preview
fun aa() {
//    Aria2Dialog()
    BaseDialog("title", "", "context") {

    }
}

@Composable
@Preview
fun ab() {
//    Aria2Dialog()
    InfoDialog(
        onDismissRequest = { },
        onConfirmation = {
            println("Confirmation registered") // Add logic here to handle confirmation.
        },
        dialogTitle = "Alert dialog example"
    )

}


@ExperimentalMaterial3Api
@Composable
@Preview
fun bb() {
    RadioButtonDialog(stringArrayResource(id = R.array.fileOrder).toList()) {}
}

