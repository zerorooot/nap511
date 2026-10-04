package github.zerorooot.nap511.terminal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.zerorooot.nap511.terminal.engine.CandidateType
import github.zerorooot.nap511.terminal.engine.CompletionCandidate

/**
 * 终端智能自动补全选项栏 (Chips Bar)
 *
 * 位于软键盘/悬浮栏上方，支持双击 Tab (Tab+Tab) 呼出所有匹配项。
 * 支持横向惯性滚动、点击直接补全，以及外接实体键盘连续按 Tab 键时循环高亮并自动滚动定位。
 *
 * @param candidates 补全候选列表
 * @param isVisible 候选栏是否可见
 * @param activeIndex 当前处于焦点高亮状态的候选索引 (-1 表示未选中)
 * @param onSelectCandidate 选中候选时的回调
 * @param onDismiss 点击关闭按钮或收起回调
 */
@Composable
fun TerminalCompletionBar(
    candidates: List<CompletionCandidate>,
    isVisible: Boolean,
    activeIndex: Int,
    onSelectCandidate: (CompletionCandidate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()

    // 当连续按 Tab 轮转高亮时，自动将高亮 Chip 滚动到可视区域中央
    LaunchedEffect(activeIndex) {
        if (activeIndex in candidates.indices) {
            lazyListState.animateScrollToItem(activeIndex)
        }
    }

    AnimatedVisibility(
        visible = isVisible && candidates.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF181818),
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 水平滚动候选项列表
                LazyRow(
                    state = lazyListState,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(
                        items = candidates,
                        key = { _, candidate -> "${candidate.type}_${candidate.name}" }
                    ) { index, candidate ->
                        CompletionChip(
                            candidate = candidate,
                            isSelected = (index == activeIndex),
                            onClick = { onSelectCandidate(candidate) }
                        )
                    }
                }

                // 右侧常驻关闭按钮
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(end = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭补全栏",
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 单个候选 Chip 标签
 */
@Composable
private fun CompletionChip(
    candidate: CompletionCandidate,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, iconColor) = when (candidate.type) {
        CandidateType.DIRECTORY -> Pair(Icons.Default.Folder, Color(0xFF81D4FA))
        CandidateType.FILE -> Pair(Icons.AutoMirrored.Filled.InsertDriveFile, Color(0xFFB0BEC5))
        CandidateType.COMMAND -> Pair(Icons.Default.Terminal, Color(0xFF69F0AE))
        CandidateType.FLAG -> Pair(Icons.Default.Flag, Color(0xFFFFD54F))
    }

    val backgroundColor = if (isSelected) Color(0xFF263238) else Color(0xFF242424)
    val borderColor = if (isSelected) Color(0xFF69F0AE) else Color(0xFF383838)
    val textColor = if (isSelected) Color(0xFF69F0AE) else Color(0xFFECEFF1)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = candidate.displayText,
            color = textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
