package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.bean.FilePathActions
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.screen.MiddleEllipsisText

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilePathBar(
    pathList: List<PathBean>,
    actions: FilePathActions,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // 路径变化时自动滚动到最右侧末尾
    LaunchedEffect(pathList.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = actions.onPathClick,
                onDoubleClick = actions.onPathDoubleClick,
                onLongClick = {
                    val path = pathList.last()
                    actions.onPathLongClick.invoke(path.name, path.cid)
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            pathList.forEachIndexed { index, path ->
                // interactionSource 以便组件和点击修饰符同步水波纹与焦点状态
                val interactionSource = remember { MutableInteractionSource() }
                Box {
                    FilterChip(
                        selected = (index != pathList.size - 1),
                        onClick = {},
                        label = {
                            Text(text = path.name.ifEmpty { "根目录" })
                        },
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp),
                        interactionSource = interactionSource
                    )
                    // 添加一个完全匹配尺寸的透明层，统一处理单击和长按
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = null, // 彻底关闭浮层的水波纹渲染，由底层 Chip 自行展示
                                onClick = { actions.onPathItemClick(path.cid) },
                                onLongClick = {
                                    actions.onPathLongClick.invoke(
                                        path.name,
                                        path.cid
                                    )
                                }
                            )
                    )
                }

                // 间隔符
                if (index < pathList.size - 1) {
                    MiddleEllipsisText(
                        text = "/",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
//                        modifier = Modifier.padding(0.dp, 4.dp)
                    )
                }
            }
        }
    }
}
