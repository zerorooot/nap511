package github.zerorooot.nap511.dialog

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.bean.TorrentFileListWeb
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.screenitem.TorrentFileCellItem
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings

@Composable
fun CreateSelectTorrentFileDialog(
    torrentBean: TorrentFileBean,
    enter: (infoHash: String, savePath: String, wanted: String) -> Unit
) {
    if (!torrentBean.state) {
        return
    }

    val infoHash = torrentBean.infoHash
    val savePath = torrentBean.torrentName

    var isSort by remember { mutableStateOf(false) }
    val torrentFileListWeb = remember(torrentBean) {
        mutableStateListOf<TorrentFileListWeb>().apply {
            addAll(torrentBean.torrentFileListWeb)
        }
    }

    LaunchedEffect(Unit) {
        isSort = SettingsRepository.getDataSuspend(ConfigKeyUtil.TORRENT_SORT, false)
    }

    if (isSort) {
        torrentFileListWeb.sortByDescending { it.size }
    }

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

    // 已选文件的索引与内容映射状态
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

    AlertDialog(
        //约束 CommonDialogs.kt、FileInfoDialogs.kt 与 TorrentDialogs.kt 在宽屏下的最大宽度 widthIn(max = 560.dp)
        modifier = Modifier.widthIn(max = 560.dp),
        onDismissRequest = ::cancel,
        confirmButton = {
            Button(
                onClick = { enter.invoke(selectMap) }
            ) {
                Text(text = "下载")
            }
        },
        dismissButton = {
            TextButton(onClick = ::cancel) {
                Text(text = "取消")
            }
        },
        title = {
            Text(
                text = "选择要下载的文件",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
            ) {
                // 1. 顶部摘要卡片
                TorrentSummaryCard(
                    selectedCount = selectMap.size,
                    totalCount = fileCount,
                    selectedSize = selectMap.values.sumOf { it.size },
                    totalSizeString = fileSizeString
                )

                // 2. 批量操作按钮栏
                TorrentBatchActionsRow(
                    onSelectAll = ::selectAll,
                    onReversal = ::reversal,
                    onDefault = ::default
                )

                // 3. 文件列表区域
                LazyColumnScrollbar(
                    state = listState,
                    settings = ScrollbarSettings.Default.copy(
                        thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
                    )
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(
                            items = torrentFileListWeb,
                            key = { _, item -> item.hashCode() }
                        ) { index, item ->
                            TorrentFileCellItem(
                                item = item,
                                isSelected = isSelectedItem(index),
                                onClick = { onChangeState(index, item) }
                            )
                        }
                    }
                }
            }
        }
    )
}

/**
 * 种子文件选择摘要卡片，展示已选文件数量与大小、总文件数量与大小。
 */
@Composable
private fun TorrentSummaryCard(
    selectedCount: Int,
    totalCount: Int,
    selectedSize: Long,
    totalSizeString: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已选: $selectedCount / $totalCount 个文件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Formatter.formatFileSize(App.instance, selectedSize),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "总计: $totalCount 个文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Text(
                    text = totalSizeString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * 批量选择操作按钮栏（全选、反选、默认）。
 */
@Composable
private fun TorrentBatchActionsRow(
    onSelectAll: () -> Unit,
    onReversal: () -> Unit,
    onDefault: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onSelectAll,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(text = "全选", style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
            onClick = onReversal,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(text = "反选", style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
            onClick = onDefault,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(text = "默认", style = MaterialTheme.typography.labelMedium)
        }
    }
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
