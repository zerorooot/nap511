package github.zerorooot.nap511.screen.viewer.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.zerorooot.nap511.util.subtitle.SubtitleEntry

/**
 * 全屏 / 扩展歌词滚动视图组件
 *
 * 用于替换专辑封面位置，提供可滚动的全文本歌词列表：
 * - 自动平滑滚动：当 [currentIndex] 随播放时间更新时，通过 [LaunchedEffect] 自动将当前歌词行滚动至近中央位置；
 * - 交互跳转：点击任意歌词行触发 [onEntryClick] 回调，实现 Seek 到该句歌词时间点；
 * - 退出全屏：点击卡片空白处触发 [onClose] 切换回封面模式。
 *
 * @param entries 字幕/歌词条目列表
 * @param currentIndex 当前正在播放的字幕条目索引
 * @param onEntryClick 点击指定字幕条目的跳转回调
 * @param onClose 关闭全屏歌词模式的回调
 * @param modifier 布局修饰符
 */
@Composable
fun FullLyricsView(
    entries: List<SubtitleEntry>,
    currentIndex: Int,
    onEntryClick: (SubtitleEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // 监听当前歌词索引变更，实现自动跟随滚动
    LaunchedEffect(currentIndex) {
        if (currentIndex in entries.indices) {
            // 将当前行提前 5 行显示，使播放行接近中央
            listState.animateScrollToItem(
                index = (currentIndex - 5).coerceAtLeast(0)
            )
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClose() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无有效字幕数据，点击切回封面")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(entries) { index, entry ->
                    val isSelected = index == currentIndex
                    // 当前正在播放的歌词高亮放大显示
                    val textColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    val fontSize = if (isSelected) 18.sp else 14.sp

                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = fontWeight,
                            fontSize = fontSize,
                            textAlign = TextAlign.Center
                        ),
                        color = textColor,
                        modifier = Modifier
                            .clickable { onEntryClick(entry) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
