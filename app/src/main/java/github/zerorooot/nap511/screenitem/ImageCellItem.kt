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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageCellItem(
    fileBean: FileBean,
    index: Int,
    gridCellMinSize: Dp,
    //删除会有动画
    modifier: Modifier = Modifier,
    clickIndex: Int = -1,
    itemOnClick: (Int) -> Unit,
    itemOnLongClick: (Int) -> Unit,
) {
    val image = fileBean.fileIco
    val name = fileBean.name
    val imageData = fileBean.photoThumb.ifEmpty { image }

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
            .width(gridCellMinSize)
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
                    .aspectRatio(0.8f) // 1:1.5 长方形，图片高度随宽度自动计算
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
                            .crossfade(true)
                            .build(),
                        contentDescription = "File Thumbnail",
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
        gridCellMinSize = 160.dp,
        clickIndex = -1,
        itemOnClick = {},
        itemOnLongClick = {}
    )
}


