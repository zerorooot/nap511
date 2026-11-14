package github.zerorooot.nap511.ui.components.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.R
import github.zerorooot.nap511.data.model.OfflineTask
import github.zerorooot.nap511.ui.components.common.MenuItemAction
import github.zerorooot.nap511.ui.components.common.OfflineFileMoreMenu

@Composable
fun OfflineCellItem(
    offlineTask: OfflineTask,
    modifier: Modifier = Modifier,
    itemOnClick: (OfflineTask) -> Unit,
    menuOnClick: (MenuItemAction) -> Unit
) {
    val image = if (offlineTask.fileId == "") R.drawable.other else R.drawable.folder
    val name = offlineTask.name
    val size = offlineTask.sizeString
    val time = offlineTask.timeString
    val percent = offlineTask.percentString

    BaseCellCard(
        modifier = modifier,
        onClick = {
            XLog.d("OfflineCellItem [点击] name=$name")
            itemOnClick.invoke(offlineTask)
        },
        leadingContent = {
            FileThumbnail(
                iconRes = image,
                contentDescription = "Offline Task Icon"
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
        metadataContent = {
            BaseCellMetadata(
                prefixContent = if (percent.isNotEmpty()) {
                    {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                )
                        ) {
                            Text(
                                text = percent,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                maxLines = 1
                            )
                        }
                    }
                } else null,
                metaList = listOf(size, time)
            )
        },
        trailingContent = {
            OfflineFileMoreMenu { itemAction, _ ->
                XLog.d("OfflineCellItem [菜单点击] action=$itemAction, name=$name")
                menuOnClick.invoke(itemAction)
            }
        }
    )
}

@Preview
@Composable
fun p() {
    val offlineTask = OfflineTask(
        name = "test file",
        sizeString = "417.26G",
        percentString = "43%",
        timeString = "2023-02-13 12:43"
    )
    OfflineCellItem(
        offlineTask = offlineTask,
        itemOnClick = {},
        menuOnClick = { _ -> }
    )
}