package github.zerorooot.nap511.screenitem

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileItemActions
import github.zerorooot.nap511.bean.ImageBean
import github.zerorooot.nap511.util.getCoilCacheUrl

// 全局内存缓存图片宽高比，避免快速滑动和 Item 离屏复用时高度重置引发瀑布流跳动闪烁
// 限定最多保存 1150 张图片的宽高比，超过后自动淘汰最久未使用的条目
private val aspectRatioCache = object : LruCache<String, Float>(1150) {}

@Composable
fun ImageCellItem(
    fileBean: FileBean,
    index: Int,
    //删除会有动画
    modifier: Modifier = Modifier,
    clickIndex: Int = -1,
    imageBean: ImageBean? = null,
    isImageHdPreview: Boolean = false,
    itemActions: FileItemActions,
) {
    val image = fileBean.fileIco
    var name by rememberFileDisplayName(fileBean)
    val hdUrl = imageBean?.url
    val isHdLoaded = isImageHdPreview && !hdUrl.isNullOrEmpty()
    val cacheKey = fileBean.fileId.ifEmpty { fileBean.pickCode.ifEmpty { name } }
    val isStar = fileBean.isStar
    val isTop = fileBean.isPinned
    val isVideo = fileBean.isVideo == 1
    val playLong = fileBean.playLongString
    val playProgress = if (fileBean.playLong > 0 && fileBean.currentPlayTime > 0) {
        (fileBean.currentPlayTime.toFloat() / fileBean.playLong.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // 开启高清模式且 photoThumb 非空且未缓存过 ImageBean 时发起请求；
    // 将 fileBean.photoThumb 纳入 Key，确保此前请求失败（imageBean 仍为 null）并在下拉刷新获取新缩略图后能重新触发请求
    if (isImageHdPreview && fileBean.photoThumb.isNotEmpty() && imageBean == null) {
        LaunchedEffect(fileBean.pickCode, index, fileBean.photoThumb) {
            itemActions.onLoadImage?.invoke(fileBean)
        }
    }

    // 优先读取缓存的宽高比，未缓存时默认 1.0f (正方形)
    var aspectRatio by remember(cacheKey) {
        mutableFloatStateOf(aspectRatioCache[cacheKey] ?: 1.0f)
    }

    BaseVerticalCellCard(
        modifier = modifier,
        isSelected = fileBean.isSelect,
        isHighlighted = (clickIndex == index) && !fileBean.isSelect,
        onClick = {
            XLog.d("ImageCellItem [点击] index=$index, name=${fileBean.name}")
            itemActions.onItemClick.invoke(index)
        },
        onLongClick = {
            XLog.d("ImageCellItem [长按] index=$index, name=${fileBean.name}")
            itemActions.onItemLongClick.invoke(index)
        },
        mediaContent = {
            // 顶部贴边大图预览区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio.coerceIn(0.65f, 1.5f))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (fileBean.photoThumb.isEmpty()) {
                    // 无缩略图：居中显示文件图标
                    Image(
                        painter = painterResource(image),
                        contentDescription = "File Icon",
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    val context = LocalContext.current
                    val coilCacheKey = if (isHdLoaded) fileBean.pickCode else fileBean.fileId
                    val imageData = getCoilCacheUrl(context.imageLoader, coilCacheKey)
                        ?: if (isHdLoaded) hdUrl else fileBean.photoThumb

                    val imageRequestBuilder = ImageRequest.Builder(context)
                        .data(imageData)
                        .memoryCacheKey(coilCacheKey)
                        .diskCacheKey(coilCacheKey)
                        .error(R.drawable.ic_image_broken)
                        .crossfade(false) // 关闭淡入淡出动画，避免快速滑动时图片闪烁

                    // 当 isHdLoaded 为 true 时，优先使用内存缓存中 key 为 fileBean.fileId 的缩略图作为占位符
                    if (isHdLoaded && fileBean.photoThumb.isNotEmpty()) {
                        imageRequestBuilder.placeholderMemoryCacheKey(fileBean.fileId)
                    }
                    // 兜底占位图标
                    imageRequestBuilder.placeholder(image)

                    AsyncImage(
                        model = imageRequestBuilder.build(),
                        contentDescription = "File Thumbnail",
                        onSuccess = { successState ->
                            // 缩略图或高清图加载成功时，若名称处于过期提示状态则立即恢复为真实文件名
                            if (name != fileBean.name) {
                                name = fileBean.name
                            }
                            XLog.d("ImageCellItem [图片加载成功] index=$index, name=${fileBean.name}, isLoaded=$isHdLoaded, imageData=$imageData, source=${successState.result.dataSource}")
                            val drawable = successState.result.drawable
                            if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                                val newRatio =
                                    drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()
                                if (!aspectRatioCache.snapshot().containsKey(cacheKey)) {
                                    aspectRatioCache.put(cacheKey, newRatio)
                                    aspectRatio = newRatio
                                }
                            }
                        },
                        onError = { errorState ->
                            name = EXPIRED_IMAGE_TIP
                            XLog.e(
                                "ImageCellItem [图片加载失败] index=$index, name=${fileBean.name}, isLoaded=$isHdLoaded, url=$imageData, error=${errorState.result.throwable.message}"
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // 1. 置顶与收藏星标微徽章（左上角半透明黑底胶囊，水平并排）
                if (isTop || isStar) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.5.dp)
                    ) {
                        if (isTop) {
                            Box(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                    .padding(2.5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "Pinned",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        if (isStar) {
                            Box(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                    .padding(2.5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "Starred",
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                // 2. 视频时长半透明胶囊（右下角）
                if (isVideo && playLong.isNotEmpty()) {
                    val bottomPadding = if (playProgress > 0f) 6.dp else 4.dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 4.dp, bottom = bottomPadding)
                            .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = playLong.trim(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 3. 视频播放进度条（底部 3.5dp 平直高对比进度条）
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
        },
        textContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 1. 文件名（最多 2 行展示）
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // 2. 属性元数据（文件大小、进度比例与创建时间）
                BaseCellMetadata(
                    metaList = listOf(
                        fileBean.playLongRatio.trim(),
                        fileBean.sizeString.trim(),
                        fileBean.createTimeString.trim()
                    )
                )
            }
        }
    )
}



