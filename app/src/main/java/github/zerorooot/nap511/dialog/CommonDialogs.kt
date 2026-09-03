package github.zerorooot.nap511.dialog

import android.os.Process
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.google.gson.JsonObject
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import github.zerorooot.nap511.viewmodel.closeTextBodyDialog
import kotlinx.coroutines.delay
import java.nio.charset.Charset
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BaseDialog(
    title: String,
    label: String,
    context: String = "",
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    confirmButtonText: String = "确认",
    dismissButtonText: String = "取消",
    selection: TextRange = TextRange(context.length),
    enter: (String?) -> Unit,
) {
    var text by remember {
        mutableStateOf(
            TextFieldValue(
                text = context, selection = selection
            )
        )
    }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        modifier = Modifier.width(IntrinsicSize.Max),
        onDismissRequest = {
            enter.invoke(null)
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
                            null
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
                    .fillMaxWidth()
                    .heightIn(1.dp, Dp.Infinity),
                readOnly = readOnly,
                textStyle = if (readOnly) LocalTextStyle.current.copy(textAlign = TextAlign.Center) else LocalTextStyle.current,
                label = { Text(text = label) },
                trailingIcon = {
                    if (!readOnly) {
                        IconButton(onClick = { text = TextFieldValue("") }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "clear"
                            )
                        }
                    }
                },
                onValueChange = {
                    text = it
                },
            )
        }, shape = MaterialTheme.shapes.medium, properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    )
    LaunchedEffect(Unit) {
        delay(10.milliseconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun InfoDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
) {
    AlertDialog(title = {
        Text(text = dialogTitle)
    }, onDismissRequest = {
        onDismissRequest()
    }, confirmButton = {
        Button(onClick = {
            onConfirmation()
        }) {
            Text("确定")
        }
    }, dismissButton = {
        TextButton(onClick = {
            onDismissRequest()
        }) {
            Text("取消")
        }
    })
}

@Composable
fun RadioButtonDialog(
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
                    verticalAlignment = Alignment.CenterVertically, modifier = Modifier
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
                        tint = MaterialTheme.colorScheme.primary
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
fun ExitApp(onDismissRequest: () -> Unit) {
    var isOpen by remember {
        mutableStateOf(true)
    }
    if (isOpen) {
        InfoDialog(
            onDismissRequest = {
                isOpen = false
                onDismissRequest.invoke()
            },
            onConfirmation = {
                Process.killProcess(Process.myPid())
                exitProcess(1)
            },
            dialogTitle = "是否离开nap511?",
        )
    }
}

@Composable
fun CookieDialog(enter: (String?) -> Unit) {
    var isOpen by remember {
        mutableStateOf(true)
    }

    if (isOpen) {
        BaseDialog(
            title = "设置Cookie", label = "请输入Cookie", dismissButtonText = "通过网页登陆"
        ) {
            enter.invoke(it)
            isOpen = false
        }
    }
}

@Composable
fun RecyclePasswordDialog(recycleViewModel: RecycleViewModel, enter: (String?) -> Unit) {
    if (recycleViewModel.isOpenRecyclePasswordDialog) {
        BaseDialog(
            title = "请输入6位数字安全密钥",
            label = "数字安全密钥",
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            enter = enter
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchDialog(
    search: (searchKey: String?) -> Unit,
    onSelectStrategy: (strategy: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val strategies = remember {
        listOf(
            "视频" to Icons.Default.Movie,
            "音频" to Icons.Default.MusicNote,
            "图片" to Icons.Default.Image,
            "文档" to Icons.Default.Description,
            "软件" to Icons.Default.Apps,
            "压缩" to Icons.Default.FolderZip
        )
    }

    AlertDialog(
        onDismissRequest = { search.invoke(null) },
        title = {
            Text(
                text = "在当前目录下搜索",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text("输入关键字...") },
                    singleLine = true,
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { text = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清空输入")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (text.isNotBlank()) search(text)
                        }
                    )
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "按文件类型筛选",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        strategies.forEach { (label, icon) ->
                            FilterChip(
                                selected = true,
                                onClick = { onSelectStrategy(label) },
                                label = { Text(label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { search(text.takeIf { it.isNotBlank() }) }
            ) {
                Text("搜索")
            }
        },
        dismissButton = {
            TextButton(onClick = { search.invoke(null) }) {
                Text("取消")
            }
        },
        shape = MaterialTheme.shapes.medium
    )

    LaunchedEffect(Unit) {
        delay(10.milliseconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun Aria2Dialog(context: String, enter: (String) -> Unit) {
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

    AlertDialog(
        onDismissRequest = {
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
                    modifier = Modifier.focusRequester(focusRequester),
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
            usePlatformDefaultWidth = false
        )
    )
    LaunchedEffect(Unit) {
        delay(10.milliseconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun TextBodyDialog(fileViewModel: FileViewModel) {
    val textBytes = fileViewModel.textBodyByteArray
    if (textBytes != null) {
        val fileBean = fileViewModel.fileBeanList.getOrNull(fileViewModel.selectIndex)
        TextBodyDialogScreen(fileBean?.name ?: "文本预览", textBytes) {
            if (it == "") {
                fileViewModel.textBodyByteArray = null
                fileViewModel.closeTextBodyDialog()
            }
        }
    }
}

@Composable
fun TextBodyDialogScreen(title: String, context: ByteArray, enter: (String) -> Unit) {
    var charsetText by remember {
        mutableStateOf(
            TextFieldValue(
                text = "UTF-8"
            )
        )
    }
    var contentText by remember {
        mutableStateOf(
            TextFieldValue(
                text = context.toString(Charset.forName(charsetText.text))
            )
        )
    }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(onDismissRequest = {
        enter.invoke("")
    }, confirmButton = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.offset(y = (-20).dp)
        ) {
            TextButton(
                onClick = {
                    enter.invoke("")
                },
            ) {
                Text(text = "关闭")
            }
        }
    }, title = { Text(text = title) }, text = {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            OutlinedTextField(
                value = charsetText,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        "clear",
                        modifier = Modifier.clickable(onClick = {
                            charsetText = TextFieldValue("")
                        })
                    )
                },
                label = { Text(text = "文件编码") },
                onValueChange = {
                    charsetText = it
                    val charset = try {
                        Charset.forName(it.text)
                    } catch (_: Exception) {
                        Charset.defaultCharset()
                    }
                    contentText = TextFieldValue(context.toString(charset))
                },
            )
            OutlinedTextField(
                value = contentText,
                label = { Text(text = "文件内容") },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .heightIn(1.dp, Dp.Infinity),
                onValueChange = {
                    contentText = it
                },
            )
        }
    })
}
