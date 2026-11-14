package github.zerorooot.nap511.ui.components.item

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import github.zerorooot.nap511.R
import github.zerorooot.nap511.util.getCoilCacheUrl

/**
 * 现代 MD3 单层卡片插槽式底座容器
 *
 * 负责全应用文件条目的统一外形、交互手势（点击、长按）、选中高亮与边框反馈。
 * 遵循高内聚低耦合原则，modifier 仅控制自身边界，外边距由外层列表或网格排版控制。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BaseCellCard(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isHighlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leadingContent: @Composable () -> Unit,
    titleContent: @Composable () -> Unit,
    subtitleContent: (@Composable () -> Unit)? = null,
    metadataContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val cardShape = RoundedCornerShape(12.dp)
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isHighlighted -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    val clickModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick ?: {},
            onLongClick = onLongClick
        )
    } else {
        Modifier
    }

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(cardShape) // 先进行形状裁切，确保长按与点击水波纹严格限制在 12dp 圆角内部
            .then(clickModifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧槽位：52dp 图标或缩略图
                leadingContent()

                // 中间内容列：紧凑居中对齐标题、副标题（路径等）、元数据
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 12.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
                ) {
                    titleContent()
                    subtitleContent?.invoke()
                    metadataContent?.invoke()
                }

                // 右侧槽位：更多菜单按钮或操作组件
                trailingContent?.invoke()
            }
        }
    }
}

/**
 * 现代 MD3 垂直瀑布流卡片插槽式底座容器
 *
 * 专为图片/多媒体瀑布流（ImageCellItem 等）设计，与 BaseCellCard 共享统一的设计语言与视觉令牌：
 * 12dp 平滑圆角、未选中微边框、选中品牌色高亮与 1.5dp 边框、点击水波纹内部裁切。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BaseVerticalCellCard(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isHighlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    mediaContent: @Composable () -> Unit,
    textContent: (@Composable () -> Unit)? = null,
) {
    val cardShape = RoundedCornerShape(12.dp)
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isHighlighted -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    val clickModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick ?: {},
            onLongClick = onLongClick
        )
    } else {
        Modifier
    }

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape) // 先进行形状裁切，确保长按与点击水波纹严格限制在 12dp 圆角内部
            .then(clickModifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 顶部媒体展示区（贴边大图）
                mediaContent()

                // 底部文字与元数据区域
                textContent?.invoke()
            }
        }
    }
}

/**
 * 统一 52dp 文件缩略图底座组件
 *
 * 支持普通文件矢量图标居中内缩、异步图片/视频海报裁切填充加载、Coil 缓存获取、
 * 视频右下角深色时长胶囊与底边播放进度指示。
 */
@Composable
fun FileThumbnail(
    iconRes: Int,
    modifier: Modifier = Modifier,
    thumbUrl: String = "",
    cacheKey: String = "",
    isTop: Boolean = false,
    isStar: Boolean = false,
    isVideo: Boolean = false,
    videoDuration: String = "",
    playProgress: Float = 0f,
    contentDescription: String = "Thumbnail",
    onLoadSuccess: (() -> Unit)? = null,
    onLoadError: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbUrl.isEmpty()) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(32.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            val context = LocalContext.current
            val imageLoader = context.imageLoader
            val imageData = getCoilCacheUrl(imageLoader, cacheKey) ?: thumbUrl

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageData)
                    .memoryCacheKey(cacheKey.ifEmpty { null })
                    .diskCacheKey(cacheKey.ifEmpty { null })
                    .placeholder(iconRes)
                    .error(R.drawable.ic_image_broken)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = {
                    onLoadSuccess?.invoke()
                },
                onError = {
                    onLoadError?.invoke()
                }
            )
        }

        // 置顶与星标左上角并排半透明徽标（彻底解放文字行的水平排版空间）
        if (isTop || isStar) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isTop) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                            .padding(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
                if (isStar) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                            .padding(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Starred",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }

        // 视频时长半透明胶囊
        if (isVideo && videoDuration.isNotEmpty()) {
            val bottomPadding = if (playProgress > 0f) 5.5.dp else 3.dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 2.5.dp, bottom = bottomPadding)
                    .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 2.5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = videoDuration.trim(),
                    color = Color.White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // 视频播放进度条（3.5dp 高对比平直底轨，确保在圆角裁切和复杂封面上清晰可见）
        if (isVideo && playProgress > 0f) {
            LinearProgressIndicator(
                progress = { playProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Black.copy(alpha = 0.65f),
                strokeCap = StrokeCap.Butt
            )
        }
    }
}

/**
 * 统一文件元数据展示行
 *
 * 规范化展示星标、前缀自定义胶囊（如离线百分比），并以中圆点（·）分隔多段文本属性。
 */
@Composable
fun BaseCellMetadata(
    modifier: Modifier = Modifier,
    isStar: Boolean = false,
    metaList: List<String> = emptyList(),
    prefixContent: (@Composable () -> Unit)? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
    ) {
        if (isStar) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Starred",
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(13.dp)
            )
        }
        prefixContent?.invoke()

        val text = metaList.filter { it.isNotBlank() }.joinToString("\u2009·\u2009")
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}
