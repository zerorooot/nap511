package github.zerorooot.nap511.screenitem

import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.ImageBean


// 全局内存缓存图片宽高比，避免快速滑动和 Item 离屏复用时高度重置引发瀑布流跳动闪烁
// 限定最多保存 1150 张图片的宽高比，超过后自动淘汰最久未使用的条目
private val aspectRatioCache = object : LruCache<String, Float>(1150) {}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageCellItem(
    fileBean: FileBean,
    index: Int,
    //删除会有动画
    modifier: Modifier = Modifier,
    clickIndex: Int = -1,
    imageBean: ImageBean? = null,
    isImageHdPreview: Boolean = false,
    onLoadImage: ((Int) -> Unit)? = null,
    itemOnClick: (Int) -> Unit,
    itemOnLongClick: (Int) -> Unit,
) {
    val image = fileBean.fileIco
    val name = fileBean.name
    val hdUrl = imageBean?.url
    val isHdLoaded = isImageHdPreview && !hdUrl.isNullOrEmpty()
    val imageData = if (isHdLoaded) hdUrl else fileBean.photoThumb.ifEmpty { image }
    val cacheKey = fileBean.fileId.ifEmpty { fileBean.pickCode.ifEmpty { name } }
    val coilCacheKey = if (isHdLoaded) fileBean.pickCode else fileBean.fileId

    // 开启高清模式且 photoThumb 非空且未缓存过 ImageBean 时发起请求
    if (isImageHdPreview && fileBean.photoThumb.isNotEmpty() && imageBean == null) {
        LaunchedEffect(fileBean.pickCode, index) {
          //  XLog.d("ImageCellItem [触发高清图请求] index=$index, name=${fileBean.name}, pickCode=${fileBean.pickCode}")
            onLoadImage?.invoke(index)
        }
    }

    // 优先读取缓存的宽高比，未缓存时默认 1.0f (正方形)
    var aspectRatio by remember(cacheKey) {
        mutableFloatStateOf(aspectRatioCache[cacheKey] ?: 1.0f)
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (fileBean.isSelect) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (clickIndex == index) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .combinedClickable(
                onClick = {
                    XLog.d("ImageCellItem [点击] index=$index, name=${fileBean.name}")
                    itemOnClick.invoke(index)
                },
                onLongClick = {
                    XLog.d("ImageCellItem [长按] index=$index, name=${fileBean.name}")
                    itemOnLongClick.invoke(index)
                }
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 顶部图片/缩略图预览区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio.coerceIn(0.65f, 1.5f))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (fileBean.photoThumb.isEmpty()) {
                    // 无缩略图：居中显示文件图标
                    Image(
                        painter = painterResource(image),
                        contentDescription = "File Icon",
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // 有缩略图：按比例裁切填充显示照片
                    val imageRequestBuilder = ImageRequest.Builder(LocalContext.current)
                        .data(imageData)
                        .memoryCacheKey(coilCacheKey)
                        .diskCacheKey(coilCacheKey)
                        .error(image)
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
                            XLog.d("ImageCellItem [图片加载成功] index=$index, name=${fileBean.name}, isLoaded=$isHdLoaded, source=${successState.result.dataSource}")
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
                            XLog.e(
                                "ImageCellItem [图片加载失败] index=$index, name=${fileBean.name}, isLoaded=$isHdLoaded, url=$imageData",
                                errorState.result.throwable
                            )
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.medium),// 设置圆角曲率与 Card 保持一致
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // 底部文件名文本
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Preview
@Composable
fun ImageCellItemPreview() {
    val copy =
        FileBean().copy(
            photoThumb = "https://my.115.com/static/2014v1.0/personal/head/80/male/male034.png",
            name = "图片文件测试123.jpg",
            fileIco = R.drawable.png
        )
    ImageCellItem(
        fileBean = copy,
        index = 1,
        clickIndex = -1,
        itemOnClick = {},
        itemOnLongClick = {}
    )
}



