package github.zerorooot.nap511.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.ForceOpenType
import github.zerorooot.nap511.bean.VideoBean
import kotlin.math.max

@Composable
fun ForceOpenDialog(
    fileName: String,
    onDismissRequest: () -> Unit,
    onTypeSelected: (ForceOpenType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "把文件 '$fileName' 强行打开为",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ForceOpenType.entries.forEach { type ->
                    FilterChip(
                        selected = true,
                        onClick = {
                            onTypeSelected(type)
                            onDismissRequest()
                        },
                        label = { Text(type.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = type.icon,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        }
    )
}

@Composable
fun CreateFolderDialog(enter: (String?) -> Unit) {
    BaseDialog("请输入新建文件名", "文件名", enter = enter)
}

@Composable
fun RenameFileDialog(name: String, position: Boolean, enter: (String?) -> Unit) {
    val atPosition = max(name.lastIndexOf("@"), name.lastIndexOf(" ")) + 1
    BaseDialog(
        "重命名文件", "新文件名", name, enter = enter, selection = TextRange(
            if (!position || atPosition == 0) name.length else atPosition
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileOrderDialog(orderBean: String, enter: (String) -> Unit) {
    val fileOrderList = stringArrayResource(id = R.array.fileOrder).toList()
    RadioButtonDialog(fileOrderList, orderBean, enter)
}

/**
 * 移除批量确认删除弹窗：
 * 采用 M3 AlertDialog 规范，支持限制最大高度的滚动列表展示移除视频清单，
 * 消除用户批量删除盲操顾虑，兼具清晰度与高复用性。
 */
@Composable
fun DiscardVideosDialog(
    discardList: List<VideoBean>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.widthIn(max = 560.dp),
        onDismissRequest = onDismiss,
        title = {
            Text(text = "确认删除移除视频", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "本次共标记了 ${discardList.size} 个移除视频，确认将其移入网盘回收站吗？",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    items(discardList) { video ->
                        Text(
                            text = "• ${video.name}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "确认删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        }
    )
}
