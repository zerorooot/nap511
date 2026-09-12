package github.zerorooot.nap511.screenitem

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
import coil.request.CachePolicy
import coil.request.ImageRequest
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean


// 全局内存缓存图片宽高比，避免快速滑动和 Item 离屏复用时高度重置引发瀑布流跳动闪烁
private val aspectRatioCache = mutableMapOf<String, Float>()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageCellItem(
    fileBean: FileBean,
    index: Int,
    //删除会有动画
    modifier: Modifier = Modifier,
    clickIndex: Int = -1,
    itemOnClick: (Int) -> Unit,
    itemOnLongClick: (Int) -> Unit,
) {
    val image = fileBean.fileIco
    val name = fileBean.name
    val imageData = fileBean.photoThumb.ifEmpty { image }
    val cacheKey = fileBean.fileId.ifEmpty { fileBean.photoThumb.ifEmpty { name } }

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
                onClick = { itemOnClick.invoke(index) },
                onLongClick = { itemOnLongClick.invoke(index) }
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
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageData)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .memoryCacheKey(fileBean.fileId)
                            .diskCacheKey(fileBean.fileId)
                            .placeholder(image)
                            .error(image)
                            .crossfade(false) // 关闭淡入淡出动画，避免快速滑动时图片闪烁
                            .build(),
                        contentDescription = "File Thumbnail",
                        onSuccess = { successState ->
                            val drawable = successState.result.drawable
                            if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                                val newRatio = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()
                                if (aspectRatio != newRatio) {
                                    aspectRatioCache[cacheKey] = newRatio
                                    aspectRatio = newRatio
                                }
                            }
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



