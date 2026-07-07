package github.zerorooot.nap511.screen


import android.os.Process
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.JsonObject
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileInfo
import github.zerorooot.nap511.bean.InfoItem
import github.zerorooot.nap511.bean.InfoSection
import github.zerorooot.nap511.bean.OfflineTask
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.bean.TorrentFileListWeb
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.screenitem.AutoSizableTextField
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import github.zerorooot.nap511.viewmodel.closeTextBodyDialog
import github.zerorooot.nap511.viewmodel.closeUnzipAllFileDialog
import github.zerorooot.nap511.viewmodel.closeUnzipDialog
import github.zerorooot.nap511.viewmodel.closeUnzipPasswordDialog
import github.zerorooot.nap511.viewmodel.decryptZip
import github.zerorooot.nap511.viewmodel.getZipListFile
import github.zerorooot.nap511.viewmodel.unzipFile
import kotlinx.coroutines.delay
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CreateFolderDialog(fileViewModel: FileViewModel, enter: (String?) -> Unit) {
    if (fileViewModel.isOpenCreateFolderDialog) {
        BaseDialog("请输入新建文件名", "文件名", enter = enter)
    }
}

@Composable
fun SearchDialog(fileViewModel: FileViewModel, enter: (String?) -> Unit) {
    if (fileViewModel.isOpenSearchDialog) {
        BaseDialog("在当前目录下搜索", "关键字", enter = enter)
    }
}


@Composable
fun RenameFileDialog(fileViewModel: FileViewModel, enter: (String?) -> Unit) {
    if (fileViewModel.isOpenRenameFileDialog) {
        val name = fileViewModel.fileBeanList[fileViewModel.selectIndex].name
        val position = DataStoreUtil.getData(ConfigKeyUtil.POSITION_AFTER_AT, false)
        val atPosition = name.lastIndexOf("@") + 1
        BaseDialog(
            "重命名文件", "新文件名", name, enter = enter, selection = TextRange(
                if (!position || atPosition == 0) name.length else atPosition
            )
        )
    }
}

@Composable
fun FileInfoDialog(
    fileViewModel: FileViewModel,
    enter: () -> Unit,
    fileInfoClick: (String) -> Unit
) {
    if (fileViewModel.isOpenFileInfoDialog) {
        val fileBean = fileViewModel.fileBeanList[fileViewModel.selectIndex]
//        BaseDialog( title = fileBean.name,label = "文件信息", readOnly = true,context = fileBean.toString() + "\n" + fileViewModel.fileInfo,enter = enter)
        FileInfoDialog(fileBean, fileViewModel.fileInfo, enter, fileInfoClick)
    }
}

@Composable
private fun FileInfoDialog(
    fileBean: FileBean,
    fileInfo: FileInfo,
    onDismissRequest: () -> Unit,
    fileInfoClick: (String) -> Unit
) {
    val icon = fileBean.fileIco
    val sections = mutableListOf<InfoSection>()

    // 区块一：基础信息
    val baseItems = mutableListOf<InfoItem>()
    baseItems.add(InfoItem("类型", if (fileBean.isFolder) "文件夹" else "文件"))
    if (fileBean.isFolder) {
        baseItems.add(
            InfoItem(
                "包含内容",
                "${fileInfo.count} 个文件, ${fileInfo.folderCount} 个文件夹"
            )
        )
    }
    baseItems.add(
        InfoItem(
            "总大小",
            fileInfo.size.ifEmpty { fileBean.sizeString.ifEmpty { "0 B" } })
    )
    sections.add(InfoSection(title = "基础信息", items = baseItems))

    // 区块二：位置与共享
    val pathString = fileInfo.paths.joinToString("/") { it.fileName } + "/${fileBean.name}"
    val locationItems = listOf(
        InfoItem(
            "文件路径",
            pathString.ifEmpty { "根目录" }
        ) { fileInfoClick.invoke(fileBean.categoryId) },
        InfoItem("提取码", fileBean.pickCode.ifEmpty { "无" })
    )
    sections.add(InfoSection(title = "位置与共享", items = locationItems))

    // 区块三：时间信息
    val timeItems = listOf(
        InfoItem("创建时间", fileBean.createTimeString.ifEmpty { "未知" }),
        InfoItem("修改时间", fileBean.modifiedTimeString.ifEmpty { "未知" })
    )
    sections.add(InfoSection(title = "时间信息", items = timeItems))

    BaseDetailDialog(
        title = fileBean.name,
        icon = icon,
        sections = sections,
        onDismissRequest = onDismissRequest
    )
}

@Composable
fun BaseDetailDialog(
    title: String,
    icon: Int,
    sections: List<InfoSection>,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(icon),
                    modifier = Modifier.size(28.dp),
                    contentScale = ContentScale.Fit,
                    contentDescription = "",
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2, // 离线任务名字通常较长，放宽到3行
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.titleMedium.lineHeight
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sections.forEachIndexed { index, section ->
                    // 区块标题
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp)
                    )

                    // 区块内容
                    section.items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { item.onClick?.let { it() } } // 绑定点击事件
                                .padding(vertical = 4.dp), // 增加一点垂直内边距，让点击区域更大、更跟手,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 左侧：标签 (占 1 份)
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 2.dp) // 微调，使其与右侧第一行文本视觉对齐
                            )

                            Spacer(modifier = Modifier.width(16.dp))
                            Box(
                                modifier = Modifier.weight(2f),
                                contentAlignment = Alignment.TopEnd // 确保内容靠右
                            ) {
                                Text(
                                    text = item.value.ifEmpty { "-" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.End,
                                    minLines = 1,
                                    color = if (item.onClick != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.fillMaxWidth() // 填满 Box，遇到边界自动强制换行
                                )
                            }
                        }
                    }

                    // 分割线 (最后一个区块不显示)
                    if (index < sections.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("关闭")
            }
        }
    )
}


@Composable
fun OfflineFileInfoDialog(
    offlineFileViewModel: OfflineFileViewModel, enter: () -> Unit, copyUrl: (String) -> Unit
) {
    if (offlineFileViewModel.isOpenOfflineDialog) {
        val offlineTask = offlineFileViewModel.offlineTask
        OfflineTaskDialog(offlineTask, onDismissRequest = enter, urlCopy = copyUrl)
//        BaseDialog(title = offlineTask.name, label = "文件信息", readOnly = true, context = offlineTask.toString(), enter = enter)
    }
}

@Composable
fun OfflineTaskDialog(
    task: OfflineTask,
    onDismissRequest: () -> Unit,
    urlCopy: (String) -> Unit
) {
    val sections = listOf(
        InfoSection(
            title = "任务信息",
            items = listOf(
                InfoItem("状态", task.percentString),
                InfoItem("总大小", task.sizeString),
                InfoItem("下载进度", "${task.percentDone}%")
            )
        ),
        InfoSection(
            title = "链接与哈希",
            items = listOf(
                InfoItem("哈希值", task.infoHash),
                InfoItem("链接", task.url) {
                    urlCopy.invoke(task.url)
                }
            )
        ),
        InfoSection(
            title = "时间信息",
            items = listOf(
                InfoItem("添加时间", task.timeString)
            )
        )
    )

    BaseDetailDialog(
        title = task.name,
        icon = if (task.fileId == "") R.drawable.other else R.drawable.folder,
        sections = sections,
        onDismissRequest = onDismissRequest
    )
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

@Composable
fun ExitApp() {
    var isOpen by remember {
        mutableStateOf(true)
    }
    val fileViewModel = viewModel<FileViewModel>()
    if (isOpen) {
        InfoDialog(
            onDismissRequest = {
                fileViewModel.selectedItem = ConfigKeyUtil.MY_FILE
            },
            onConfirmation = {
                Process.killProcess(Process.myPid());
                exitProcess(1);
            },
            dialogTitle = "是否离开Nap511?",
        )
    }
}

@Composable
fun TextBodyDialog(fileViewModel: FileViewModel) {
    if (fileViewModel.isOpenTextBodyDialog  && fileViewModel.textBodyByteArray != null) {
        val fileBean = fileViewModel.fileBeanList[fileViewModel.selectIndex]
        TextBodyDialogScreen(fileBean.name, fileViewModel.textBodyByteArray!!) {
            if (it == "") {
                fileViewModel.textBodyByteArray = null
                fileViewModel.closeTextBodyDialog()
            }
        }
    }
}

@Preview
@Composable
fun TextBodyDialogPreview() {
    TextBodyDialogScreen("", "dasfasdfasfdsafs".toByteArray()) { }
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
            verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(y = (-20).dp)
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
        Column {
            OutlinedTextField(
                value = charsetText,
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

/**
 * 解压文件
 */
@Composable
fun UnzipDialog(fileViewModel: FileViewModel) {
    if (fileViewModel.isOpenUnzipPasswordDialog) {
        val fileBean = fileViewModel.fileBeanList[fileViewModel.selectIndex]
        fileViewModel.setRefreshingStatus(false)
        BaseDialog(
            title = "云解压-${fileBean.name}", label = "请输入密码", dismissButtonText = "取消"
        ) {
            XLog.d("云解压 ${fileBean.name} password $it")
            if (it != null && it != "") {
                fileViewModel.decryptZip(it)
            } else {
                fileViewModel.closeUnzipPasswordDialog()
            }
        }
    }

    if (fileViewModel.isOpenUnzipDialog) {
        val fileBean = fileViewModel.fileBeanList[fileViewModel.selectIndex]
        val zipBeanList by fileViewModel.unzipBeanList
        LaunchedEffect(Unit) {
            fileViewModel.setRefreshingStatus(false)
        }
        UnzipScreen(zipBeanList, fileBean.name) {
            //Pair true is command,false is click event
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
                        } catch (e: Exception) {
                        }
                        fileViewModel.getZipListFile(fileName, paths)
                    }

                    "unzipAll" -> {
                        fileViewModel.unzipFile()
                        fileViewModel.closeUnzipDialog()
                    }
                }
            } else {
                //go to next folder
                fileViewModel.getZipListFile(
                    it.second, paths = zipBeanList.pathString
                )
            }
        }
    }
}

@Composable
fun UnzipAllFile(
    fileViewModel: FileViewModel
) {
    if (fileViewModel.isOpenUnzipAllFileDialog) {
        BaseDialog("请输入解压密码", "如无加密，为空即可") { pwd ->
            if (pwd == null) {
                fileViewModel.closeUnzipAllFileDialog()
                return@BaseDialog
            }

            val currentCid = fileViewModel.currentCid

            fileViewModel.closeUnzipAllFileDialog()

            val message = fileViewModel.fileBeanList
                .filter { i -> i.isSelect && i.fileIco == R.drawable.zip }
                .takeIf { it.isNotEmpty() }
                ?.let {
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnzipScreen(
    zipBeanList: ZipBeanList, fileName: String, enter: (Pair<Boolean, String>) -> Unit
) {
    //Pair true is command,false is click event
    AlertDialog(onDismissRequest = {
        enter.invoke(Pair(true, "exit"))
    }, confirmButton = {
        Row(
            verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(y = (-20).dp)
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
        Column() {
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
//                                XLog.d("click $index item $item")
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
                                text = item.fileName, modifier = Modifier.padding(start = 8.dp)
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

@Preview
@Composable
fun tt() {
    var enter: (String) -> Unit = {}
    val zipBeanList = Gson().fromJson(
        "{\"list\":[{\"file_name\":\"123\",\"size\":0,\"time\":\"\",\"ico\":\"\",\"file_category\":0},{\"file_name\":\"tt\",\"size\":0,\"time\":\"\",\"ico\":\"\",\"file_category\":0},{\"file_name\":\"sizes\",\"size\":0,\"time\":\"\",\"ico\":\"\",\"file_category\":0},{\"file_name\":\"application\",\"ico\":\"properties\",\"size\":46,\"file_category\":1,\"time\":1732623206}],\"has_file\":\"false\",\"next_marker\":\"\",\"paths\":[{\"file_name\":\"文件\"}]}",
        ZipBeanList::class.java
    )
    zipBeanList.pathString = "文件/123/nihao/hello"
    zipBeanList.list.forEach { i ->
        if (i.fileCategory == 0) {
            i.fileIco = R.drawable.folder
        } else {
            i.timeString = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                i.time.toLong() * 1000
            )
            when (i.icoString) {
                "apk" -> i.fileIco = R.drawable.apk
                "iso" -> i.fileIco = R.drawable.iso
                "zip" -> i.fileIco = R.drawable.zip
                "7z" -> i.fileIco = R.drawable.zip
                "rar" -> i.fileIco = R.drawable.zip
                "png" -> i.fileIco = R.drawable.png
                "jpg" -> i.fileIco = R.drawable.png
                "mp3" -> i.fileIco = R.drawable.mp3
                "txt" -> i.fileIco = R.drawable.txt
                "torrent" -> i.fileIco = R.drawable.torrent
            }
        }
    }

    UnzipScreen(zipBeanList, "FileBean") {

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
    fileViewModel: FileViewModel,
    offlineFileViewModel: OfflineFileViewModel,
    enter: (infoHash: String, savePath: String, wanted: String) -> Unit
) {
    if (fileViewModel.isOpenCreateSelectTorrentFileDialog) {
        val torrentBean = offlineFileViewModel.torrentBean
        // XLog.d("torrentBean: ${Gson().toJson(torrentBean)}")
        if (!torrentBean.state) {
            return
        }

        val infoHash = torrentBean.infoHash
        val savePath = torrentBean.torrentName


        var isSort by remember { mutableStateOf(false) }
        val torrentFileListWeb = remember {
            mutableStateListOf<TorrentFileListWeb>().apply {
                addAll(torrentBean.torrentFileListWeb)
            }
        }

        LaunchedEffect(Unit) {
            isSort = DataStoreUtil.getData(ConfigKeyUtil.TORRENT_SORT, false)
        }

        if (isSort) {
            torrentFileListWeb.sortByDescending { it.size }
        }
        fileViewModel.setRefreshingStatus(false)

        SelectTorrentFileDialog(
            torrentFileListWeb.toList(), torrentBean.fileCount, torrentBean.fileSizeString
        ) {
            val map = if (isSort) {
                val sortMap = hashMapOf<Int, TorrentFileListWeb>()
                val torrentFileList = it.values.toMutableList()
                torrentFileList.forEach { i ->
                    sortMap[torrentBean.torrentFileListWeb.indexOf(i)] = i
                }
                sortMap
            } else {
                it
            }
            val wanted = map.keys.joinToString(separator = ",")
            enter.invoke(infoHash, savePath, wanted)
        }

    }
}

@Composable
private fun SelectTorrentFileDialog(
    torrentFileListWeb: List<TorrentFileListWeb>,
    fileCount: Int,
    fileSizeString: String,
    enter: (Map<Int, TorrentFileListWeb>) -> Unit
) {
    val listState = rememberLazyListState()
    //按文件大小排序后，不知道会滚动到什么地方，统一滚动到顶部
    LaunchedEffect(Unit) {
        listState.requestScrollToItem(0)
    }
    //select all
    val selectMap = remember(torrentFileListWeb) {
        mutableStateMapOf<Int, TorrentFileListWeb>().apply {
            torrentFileListWeb.forEachIndexed { index, torrentFileListWeb ->
                if (torrentFileListWeb.wanted == 1) {
                    this[index] = torrentFileListWeb
                }
            }
        }
    }

    fun isSelectedItem(index: Int): Boolean = selectMap.containsKey(index)

    fun onChangeState(index: Int, item: TorrentFileListWeb) {
        if (selectMap.containsKey(index)) {
            selectMap.remove(index)
        } else {
            selectMap[index] = item
        }
    }

    fun cancel() {
        selectMap.clear()
        enter.invoke(selectMap)
    }

    fun default() {
        val defaultItems = torrentFileListWeb.mapIndexedNotNull { index, item ->
            if (item.wanted == 1) index to item else null
        }
        selectMap.clear()
        selectMap.putAll(defaultItems)
    }

    fun selectAll() {
        val allItems = torrentFileListWeb.mapIndexed { index, item -> index to item }
        selectMap.clear() // 确保没有遗留脏数据后再全量覆盖
        selectMap.putAll(allItems)
    }

    // 先通过 map 构建反选后的数据集合，再进行一次性状态赋值
    fun reversal() {
        val reversedItems = torrentFileListWeb.mapIndexedNotNull { index, item ->
            if (selectMap.containsKey(index)) null else index to item
        }
        selectMap.clear()
        selectMap.putAll(reversedItems)
    }


    // 设置最大高度为屏幕高度的80%
    val maxDialogHeight =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.65f }

    AlertDialog(onDismissRequest = ::cancel, confirmButton = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.offset(y = (-20).dp)
        ) {
            TextButton(
                onClick = ::default,
            ) {
                Text(text = "默认")
            }
            TextButton(
                onClick = {
                    enter.invoke(selectMap)
                },
            ) {
                Text(text = "下载")
            }
            TextButton(
                onClick = ::selectAll,
            ) {
                Text(text = "全选")
            }
            TextButton(
                onClick = ::reversal,
            ) {
                Text(text = "反选")
            }
            TextButton(
                onClick = ::cancel,
            ) {
                Text(text = "取消")
            }
        }
    }, title = { Text(text = "选择要下载的文件") }, text = {
        Column(
            modifier = Modifier
                .heightIn(max = maxDialogHeight) // 限制最大高度，防止文件极多时超出屏幕，变成滚动模式
        ) {
            AutoSizableTextField(
                value = "已经选择${selectMap.size}/${fileCount}个，总计：${
                    Formatter.formatFileSize(
                        App.instance, selectMap.values.sumOf { it.size })
                }\n" + "共${fileCount}个文件，总计：${fileSizeString}",
                minFontSize = 30.sp,
                maxLines = 2
            )
            LazyColumn(
//                modifier = Modifier.padding(8.dp),
                state = listState
            ) {
                itemsIndexed(items = torrentFileListWeb, key = { _, item ->
                    item.hashCode()
                }) { index, item ->
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
    })

}

@Composable
@Preview
fun SelectTorrentFileDialogPreview() {
    val torrentFileBean = Gson().fromJson(
        "{\"errcode\":0,\"errno\":0,\"error_msg\":\"\",\"errtype\":\"suc\",\"file_count\":4,\"file_size\":10345578897,\"fileSizeString\":\"10.35 GB \",\"info_hash\":\"8c9f4db08497563ef6eb01cf81199f645da0954b\",\"state\":true,\"torrent_filelist_web\":[{\"path\":\"[哪吒之魔童降世].Nezha.Birth.of.the.Demon.Child.2019.USA.UHD.BluRay.2160p.x265.DTS-HD.MA5.1-CMCT.mkv\",\"size\":10345101994,\"sizeString\":\"10.35 GB \",\"wanted\":1},{\"path\":\"哪吒之魔童降世.2019.jpg\",\"size\":251007,\"sizeString\":\"251 kB \",\"wanted\":1},{\"path\":\"[哪吒之魔童降世].Nezha.Birth.of.the.Demon.Child.2019.USA.UHD.BluRay.2160p.x265.DTS-HD.MA5.1-CMCT.mkv.jpg\",\"size\":224106,\"sizeString\":\"224 kB \",\"wanted\":1},{\"path\":\"哪吒之魔童降世.2019.txt\",\"size\":1790,\"sizeString\":\"1.79 kB \",\"wanted\":0}],\"torrent_name\":\"[哪吒之魔童降世].Nezha.Birth.of.the.Demon.Child.2019.USA.UHD.BluRay.2160p.x265.DTS-HD.MA5.1-CMCT\"}",
        TorrentFileBean::class.java
    )


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
    text: String, maxStartChars: Int = 10, // 开头显示的字符数
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
private fun BaseDialog(
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
        modifier = Modifier.width(IntrinsicSize.Max), // 1. 核心修改：让 Dialog 宽度等于子组件的最大固有宽度
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
        delay(10.milliseconds)
        focusRequester.requestFocus()
    }
}


@Composable
@Preview
fun aa() {
//    Aria2Dialog()
    BaseDialog("title", "context") {

    }
}

@Composable
@Preview
fun ab() {
//    Aria2Dialog()
    InfoDialog(
        onDismissRequest = { }, onConfirmation = {
            println("Confirmation registered") // Add logic here to handle confirmation.
        }, dialogTitle = "Alert dialog example"
    )

}


@ExperimentalMaterial3Api
@Composable
@Preview
fun bb() {
    RadioButtonDialog(stringArrayResource(id = R.array.fileOrder).toList()) {}
}

