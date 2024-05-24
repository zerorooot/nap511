package github.zerorooot.nap511.screen

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.google.gson.JsonObject
import github.zerorooot.nap511.R
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import kotlinx.coroutines.delay

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

    val activity = LocalContext.current as Activity
    if (isOpen) {
        InfoDialog(
            onDismissRequest = {
                isOpen = false
                App.selectedItem = "我的文件"
            },
            onConfirmation = {
                activity.finish()
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

@OptIn(ExperimentalMaterial3Api::class)
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
            jsonObject.addProperty(ConfigUtil.aria2Url, urlText.text)
            jsonObject.addProperty(ConfigUtil.aria2Token, tokenText.text)
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

@OptIn(ExperimentalMaterial3Api::class)
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

