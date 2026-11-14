package github.zerorooot.nap511.ui.components.item

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.ui.model.FileDisplayConfig

/**
 * 全局微光动画进度提供器（以 Lambda 延迟求值下发，避免组合阶段触发全屏重组）
 */
val LocalShimmerProgressProvider = compositionLocalOf {
    { 0f }
}

/**
 * 纯绘制阶段驱动的占位微光修饰符
 * 1. 组合阶段仅捕获 lambda 引用，0 次界面重组；
 * 2. 绘制阶段读取 progressProvider()，精确只触发 Canvas Draw 层重绘；
 * 3. 彻底规避 ShaderBrush 内部按尺寸缓存导致的动画静止冻结缺陷。
 */
@Composable
fun Modifier.shimmerPlaceholder(): Modifier {
    val progressProvider = LocalShimmerProgressProvider.current
    val baseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
    val shimmerColors = remember(baseColor, highlightColor) {
        listOf(baseColor, highlightColor, baseColor)
    }

    return this.drawBehind {
        val progress = progressProvider()
        val width = size.width
        val height = size.height
        val startX = progress * width * 1.5f
        val brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(startX - width * 0.5f, 0f),
            end = Offset(startX + width * 0.5f, height)
        )
        drawRect(brush = brush)
    }
}

/**
 * 列表与网格单条骨架：严格复用 BaseCellCard 底座，非可点击态
 */
@Composable
fun FileListItemSkeleton(modifier: Modifier = Modifier) {
    BaseCellCard(
        modifier = modifier,
        onClick = null,
        onLongClick = null,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .shimmerPlaceholder()
            )
        },
        titleContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerPlaceholder()
            )
        },
        metadataContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.38f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerPlaceholder()
            )
        }
    )
}

/**
 * 瀑布流媒体单条骨架：严格复用 BaseVerticalCellCard 底座
 */
@Composable
fun FileWaterfallItemSkeleton(
    aspectRatio: Float,
    modifier: Modifier = Modifier
) {
    BaseVerticalCellCard(
        modifier = modifier,
        onClick = null,
        onLongClick = null,
        mediaContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .shimmerPlaceholder()
            )
        },
        textContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerPlaceholder()
                )
            }
        }
    )
}

/**
 * 自适应综合骨架屏容器
 * 布局排版、间距与内边距与真实视图 100% 像素级对齐，消灭转正时的布局位移
 */
@Composable
fun FileSkeletonContent(
    displayConfig: FileDisplayConfig,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "globalShimmerTransition")
    val translateAnim = transition.animateFloat(
        initialValue = -1.0f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val progressProvider = remember(translateAnim) { { translateAnim.value } }

    CompositionLocalProvider(LocalShimmerProgressProvider provides progressProvider) {
        if (displayConfig.isPreviewActive) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(displayConfig.gridCellMinSize),
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalItemSpacing = 8.dp,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sampleRatios = listOf(
                    1.0f, 0.75f, 1.33f, 0.8f, 1.15f, 0.9f,
                    1.2f, 0.85f, 1.0f, 1.25f, 0.78f, 1.1f
                )
                items(sampleRatios.size) { index ->
                    FileWaterfallItemSkeleton(aspectRatio = sampleRatios[index])
                }
            }
        } else if (displayConfig.isExpandedScreen) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(displayConfig.gridCellMinSize),
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(12) {
                    FileListItemSkeleton()
                }
            }
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(8) {
                    FileListItemSkeleton()
                }
            }
        }
    }
}
