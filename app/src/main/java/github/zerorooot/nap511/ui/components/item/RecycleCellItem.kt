package github.zerorooot.nap511.ui.components.item

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.data.model.RecycleBean
import github.zerorooot.nap511.ui.components.common.MenuItemAction
import github.zerorooot.nap511.ui.components.common.RecycleMoreMenu

@Composable
fun RecycleCellItem(
    recycleBean: RecycleBean, //删除会有动画
    modifier: Modifier,
    index: Int,
    menuOnClick: (MenuItemAction, Int) -> Unit
) {
    val image = recycleBean.fileIco
    val name = recycleBean.fileName
    val size = recycleBean.fileSizeString
    val time = recycleBean.modifiedTimeString
    val parentName = recycleBean.parentName

    BaseCellCard(
        modifier = modifier,
        leadingContent = {
            FileThumbnail(
                iconRes = image,
                thumbUrl = recycleBean.photoThumb,
                cacheKey = recycleBean.id,
                contentDescription = "Recycle Thumbnail",
                onLoadSuccess = {
                    XLog.d("RecycleCellItem [缩略图加载成功] index=$index, name=$name")
                },
                onLoadError = {
                    XLog.w("RecycleCellItem [缩略图加载失败] index=$index, name=$name")
                }
            )
        },
        titleContent = {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        subtitleContent = if (parentName.isNotEmpty()) {
            {
                Text(
                    text = parentName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else null,
        metadataContent = {
            BaseCellMetadata(
                metaList = listOf(size, time)
            )
        },
        trailingContent = {
            RecycleMoreMenu { itemAction, _ ->
                XLog.d("RecycleCellItem [菜单点击] index=$index, action=$itemAction, name=$name")
                menuOnClick.invoke(itemAction, index)
            }
        }
    )
}
