package github.zerorooot.nap511.screen

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.zerorooot.nap511.R
import github.zerorooot.nap511.viewmodel.FileViewModel
import kotlinx.coroutines.delay

@Composable
fun CreateFolderDialog(fileViewModel: FileViewModel, enter: (String) -> Unit) {
    if (fileViewModel.isOpenCreateFolderDialog) {
        BaseDialog("请输入新建文件名", "文件名", enter = enter)
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
            context = fileBean.toString(),
            enter = enter
        )
    }
}

@Composable
fun CookieDialog(enter: (String) -> Unit) {
    var isOpen by remember {
        mutableStateOf(true)
    }

    if (isOpen) {
        BaseDialog(title = "设置Cookie", label = "请输入Cookie") {
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
private fun RadioButtonDialog(
    items: List<String>,
    selectValue: String = "",
    enter: (String) -> Unit
) {
    val selectedValue = remember { mutableStateOf(selectValue) }

    val isSelectedItem: (String) -> Boolean = { selectedValue.value == it }
    val onChangeState: (String) -> Unit = { selectedValue.value = it }

    AlertDialog(
        onDismissRequest = { enter.invoke("") },
        confirmButton = {
            TextButton(
                onClick = {
                    enter.invoke("")
                },
            ) {
                Text(text = "取消")
            }
        },
        title = { Text(text = "选择排序模式") },
        text = {
            Column(Modifier.padding(8.dp)) {
                items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .selectable(
                                selected = isSelectedItem(item),
                                onClick = {
                                    onChangeState(item)
                                    enter.invoke(item)
                                },
                                role = Role.RadioButton
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
                            text = item,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BaseDialog(
    title: String,
    label: String,
    context: String = "",
    readOnly: Boolean = false,
    enter: (String) -> Unit
) {
    var text by remember {
        mutableStateOf(
            TextFieldValue(
                text = context,
                selection = TextRange(context.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = {
            enter.invoke("")
        },
        confirmButton = {
            Button(onClick = {
                enter.invoke(text.text)
                text = TextFieldValue("")
            }) {
                Text(text = "确认")
            }
        }, dismissButton = {
            TextButton(
                onClick = {
                    enter.invoke("")
                    text = TextFieldValue("")
                },
            ) {
                Text(text = "取消")
            }
        }, title = {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }, text = {
            OutlinedTextField(
                value = text,
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
                            modifier = Modifier
                                .clickable(
                                    onClick = {
                                        text = TextFieldValue("")
                                    }
                                )
                        )
                    }
                },
                onValueChange = {
                    text = it
                },
            )
        },
        shape = MaterialTheme.shapes.medium,
        properties = DialogProperties(
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
    CookieDialog {}
}

@ExperimentalMaterial3Api
@Composable
@Preview
fun bb() {
    RadioButtonDialog(stringArrayResource(id = R.array.fileOrder).toList()) {}
}

