package github.zerorooot.nap511.dialog

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.bean.TorrentFileListWeb
import github.zerorooot.nap511.screenitem.AutoSizableTextField
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil

@Composable
fun CreateSelectTorrentFileDialog(
    torrentBean: TorrentFileBean,
    onStopRefreshing: () -> Unit,
    enter: (infoHash: String, savePath: String, wanted: String) -> Unit
) {
    if (!torrentBean.state) {
        onStopRefreshing.invoke()
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
        isSort = DataStoreUtil.getDataSuspend(ConfigKeyUtil.TORRENT_SORT, false)
    }

    if (isSort) {
        torrentFileListWeb.sortByDescending { it.size }
    }
    onStopRefreshing.invoke()

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

@Composable
private fun SelectTorrentFileDialog(
    torrentFileListWeb: List<TorrentFileListWeb>,
    fileCount: Int,
    fileSizeString: String,
    enter: (Map<Int, TorrentFileListWeb>) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        listState.requestScrollToItem(0)
    }
    val selectMap = remember(torrentFileListWeb) {
        mutableStateMapOf<Int, TorrentFileListWeb>().apply {
            torrentFileListWeb.forEachIndexed { index, item ->
                if (item.wanted == 1) {
                    this[index] = item
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
        selectMap.clear()
        selectMap.putAll(allItems)
    }

    fun reversal() {
        val reversedItems = torrentFileListWeb.mapIndexedNotNull { index, item ->
            if (selectMap.containsKey(index)) null else index to item
        }
        selectMap.clear()
        selectMap.putAll(reversedItems)
    }

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
            modifier = Modifier.heightIn(max = maxDialogHeight)
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
                            tint = MaterialTheme.colorScheme.primary
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
fun DynamicEllipsizedTextView(text: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val textWidth = remember { mutableIntStateOf(0) }

    Box(modifier = modifier.onSizeChanged { textWidth.intValue = it.width }) {
        val maxChars =
            textWidth.intValue / with(density) { 12.toDp().toPx().toInt() }
        val halfChars = maxChars / 2
        EllipsizedTextView(text, maxStartChars = halfChars, maxEndChars = halfChars)
    }
}

@Composable
fun EllipsizedTextView(
    text: String, maxStartChars: Int = 10,
    maxEndChars: Int = 10,
    ellipsis: String = "..."
) {
    val displayText = if (text.length > maxStartChars + maxEndChars) {
        text.take(maxStartChars) + ellipsis + text.takeLast(maxEndChars)
    } else {
        text
    }

    Text(text = displayText)
}
