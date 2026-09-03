package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import github.zerorooot.nap511.bean.RepeatFileItem

@Composable
fun RepeatFileCardItem(
    item: RepeatFileItem,
    modifier: Modifier = Modifier,
    onPathClick: () -> Unit
) {
    val context = LocalContext.current
    val image = item.fileIco
    val name = item.fileName
    val size = item.fileSizeString
    val time = item.userUtimeStr
    val parentName = item.path.ifEmpty { "根目录" }
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 10.dp,
        modifier = modifier.padding(1.dp)
    ) {
        Card(
            modifier = Modifier
                .padding(4.dp, 4.dp)
                .height(85.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // 左侧文件图标 / 缩略图
                Box(
                    modifier = Modifier
                        .height(60.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(image)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .memoryCacheKey(item.fileId)
                            .diskCacheKey(item.fileId)
                            .scale(Scale.FILL)
                            .crossfade(true)
                            .build(),
                        contentDescription = "File Thumbnail",
                        modifier = Modifier.size(60.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // 右侧文件属性信息
                Column(
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.7f)
                ) {
                    // 文件名（支持自适应字体/多行展示）
                    AutoSizableTextField(
                        value = name,
                        modifier = Modifier
                            .padding(start = 4.dp, top = 9.dp)
                            .fillMaxWidth(),
                        minFontSize = 10.sp,
                        maxLines = 2
                    )
                    Text(
                        modifier = Modifier
                            .padding(start = 4.dp, top = 4.dp)
                            .clickable { onPathClick() },
                        text = parentName,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )

                    // 文件大小与修改时间
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .fillMaxSize()
                    ) {
                        Text(
                            text = size,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis

                        )
                    }
                }
            }
        }
    }
}