package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.RepeatFileItem

@Composable
fun RepeatFileCardItem(
    item: RepeatFileItem,
    modifier: Modifier = Modifier,
    onPathClick: () -> Unit
) {
    val image = item.fileIco
    val name = item.fileName
    val size = item.fileSizeString
    val time = item.userUtimeStr
    val parentName = item.path.ifEmpty { "根目录" }

    BaseCellCard(
        modifier = modifier,
        leadingContent = {
            FileThumbnail(
                iconRes = image,
                cacheKey = item.fileId,
                contentDescription = "Repeat File Thumbnail"
            )
        },
        titleContent = {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        subtitleContent = {
            Text(
                text = parentName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable {
                    XLog.d("RepeatFileCardItem [路径点击] path=$parentName, name=$name")
                    onPathClick()
                }
            )
        },
        metadataContent = {
            BaseCellMetadata(
                metaList = listOf(size, time)
            )
        }
    )
}