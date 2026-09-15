package github.zerorooot.nap511.screen.viewer.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.bean.SubtitleItem
import github.zerorooot.nap511.bean.SubtitleSourceType
import github.zerorooot.nap511.viewmodel.AudioViewModel
import java.util.Locale
import kotlin.math.abs

/**
 * 有状态字幕设置弹窗（ViewModel 容器层）
 *
 * 连接 [AudioViewModel]，提取当前音乐名称、音频时长、候选字幕列表、选中字幕、偏移量等状态，
 * 并将其映射传导至无状态 [SubtitleSelectionSheet] 组件。
 *
 * @param audioViewModel 音频播放器 ViewModel
 * @param categoryId 当前网盘目录 ID（用于上传字幕至 115）
 * @param onDismiss 关闭弹窗回调
 */
@Composable
fun SubtitleSelectionSheet(
    audioViewModel: AudioViewModel,
    categoryId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val subtitleState = audioViewModel.uiState.subtitle
    val musicName = audioViewModel.uiState.playback.currentMusic?.name ?: ""
    LaunchedEffect(Unit) {
        audioViewModel.loadSubtitles()
    }

    SubtitleSelectionSheet(
        musicName = musicName,
        subtitles = subtitleState.subtitles,
        musicDurationMs = audioViewModel.durationMs,
        selectedSubtitle = subtitleState.selectedSubtitle,
        subtitleOffsetMs = subtitleState.offsetMs,
        isSubtitleSearchLoading = subtitleState.isSearchLoading,
        isSubtitleLoading = subtitleState.isLoading,
        onSearch = { keyword -> audioViewModel.loadSubtitles(searchKeyword = keyword) },
        onAddSubtitleOffset = { offset -> audioViewModel.addSubtitleOffset(offset) },
        onSetSubtitleOffset = { offset -> audioViewModel.setSubtitleOffset(offset) },
        onSelectSubtitle = { item -> audioViewModel.selectSubtitle(context.cacheDir, item) },
        onRemoveSubtitle = { audioViewModel.removeSubtitle() },
        onUploadTo115 = { item ->
            audioViewModel.uploadSubtitleTo115(
                context.cacheDir,
                item,
                categoryId
            )
        },
        onDismiss = onDismiss
    )
}

/**
 * 无状态字幕/歌词设置底部弹窗组件（Stateless UI）
 *
 * 提供完整的字幕查找与绑定交互界面：
 * 1. 关键字搜索框：默认截取歌名作为默认搜索词；
 * 2. 时间偏移调节：支持 ±0.1s, ±0.5s 以及一键重置（同步字幕微调）；
 * 3. 候选字幕列表：列出搜寻到的候选字幕（按与音频时长差值的绝对值从小到大排序），显示格式及时长文本，支持选择绑定或清除；
 * 4. 转存功能：若候选字幕来自外部网络（如迅雷），支持一键上传转存至 115 网盘。
 *
 * @param musicName 当前音频文件名
 * @param subtitles 搜寻到的候选字幕列表
 * @param musicDurationMs 当前音频总时长（毫秒）
 * @param selectedSubtitle 当前已选中的字幕条目
 * @param subtitleOffsetMs 当前字幕时间偏移毫秒数
 * @param isSubtitleSearchLoading 是否正在搜索字幕列表
 * @param isSubtitleLoading 是否正在解析加载选中的字幕文件
 * @param onSearch 执行字幕搜索回调
 * @param onAddSubtitleOffset 增加/减少字幕偏移量回调
 * @param onSetSubtitleOffset 设置特定字幕偏移量回调
 * @param onSelectSubtitle 选中某条字幕的回调
 * @param onRemoveSubtitle 清除已绑定的字幕回调
 * @param onUploadTo115 上传外源字幕到 115 网盘的回调
 * @param onDismiss 关闭弹窗回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSelectionSheet(
    musicName: String,
    subtitles: List<SubtitleItem>,
    musicDurationMs: Long = 0L,
    selectedSubtitle: SubtitleItem?,
    subtitleOffsetMs: Long,
    isSubtitleSearchLoading: Boolean,
    isSubtitleLoading: Boolean,
    onSearch: (String) -> Unit,
    onAddSubtitleOffset: (Long) -> Unit,
    onSetSubtitleOffset: (Long) -> Unit,
    onSelectSubtitle: (SubtitleItem) -> Unit,
    onRemoveSubtitle: () -> Unit,
    onUploadTo115: (SubtitleItem) -> Unit,
    onDismiss: () -> Unit
) {
    // 默认清除后缀名后的歌曲标题作为搜索关键词
    var searchInput by remember {
        mutableStateOf(if (musicName.contains(".")) musicName.substringBeforeLast(".") else musicName)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "字幕与歌词设置",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 1. 关键字搜索框
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    label = { Text("搜索字幕关键字") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        IconButton(onClick = { onSearch(searchInput) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. 时间偏移量微调控制
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "偏移: ${subtitleOffsetMs}ms",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row {
                    TextButton(onClick = { onAddSubtitleOffset(-500L) }) {
                        Text("-0.5s")
                    }
                    TextButton(onClick = { onAddSubtitleOffset(-100L) }) {
                        Text("-0.1s")
                    }
                    TextButton(onClick = { onSetSubtitleOffset(0L) }) {
                        Text("重置")
                    }
                    TextButton(onClick = { onAddSubtitleOffset(100L) }) {
                        Text("+0.1s")
                    }
                    TextButton(onClick = { onAddSubtitleOffset(500L) }) {
                        Text("+0.5s")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 3. 候选字幕列表 Header 及清除按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "候选字幕列表 (${subtitles.size})",
                    style = MaterialTheme.typography.titleMedium
                )

                if (selectedSubtitle != null) {
                    TextButton(onClick = onRemoveSubtitle) {
                        Text("清除字幕", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // 4. 字幕候选列表/加载状态/无数据状态
            if (isSubtitleSearchLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (subtitles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("未检索到可用字幕", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    itemsIndexed(subtitles) { _, item ->
                        val isSelected = item.id == selectedSubtitle?.id
                        val containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }

                        val durationStr = if (item.durationMs > 0) {
                            formatDurationMs(item.durationMs)
                        } else {
                            "未知"
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelectSubtitle(item) },
                            colors = CardDefaults.cardColors(containerColor = containerColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.simpleName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "来源: ${item.sourceType.label} | 格式: ${item.ext.uppercase()} | 时长: $durationStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isSelected && isSubtitleLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else if (item.sourceType == SubtitleSourceType.XUNLEI) {
                                    IconButton(onClick = { onUploadTo115(item) }) {
                                        Icon(
                                            Icons.Default.CloudUpload,
                                            contentDescription = "Upload to 115",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 将毫秒时间转换为标准格式文本 (HH:mm:ss 或 mm:ss)
 */
private fun formatDurationMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val sec = totalSec % 60
    val min = (totalSec / 60) % 60
    val hour = totalSec / 3600
    return if (hour > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hour, min, sec)
    } else {
        String.format(Locale.US, "%02d:%02d", min, sec)
    }
}
