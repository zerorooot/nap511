package github.zerorooot.nap511.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import github.zerorooot.nap511.bean.ForceOpenType
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.bean.OrderType
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

@Composable
private fun OrderTypeItem(
    type: OrderType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = type.icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = type.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileOrderDialog(
    selectOrderBean: OrderBean,
    showStarOrder: Boolean = false,
    enter: (OrderBean?) -> Unit
) {
    var selectedType by remember { mutableStateOf(selectOrderBean.orderType) }
    var selectedAsc by remember { mutableIntStateOf(selectOrderBean.asc) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cloudTypes = OrderType.entries.filter { !it.isLocalOnly && (it.isShow || showStarOrder) }
    val localTypes = OrderType.entries.filter { it.isLocalOnly }

    ModalBottomSheet(
        onDismissRequest = { enter(null) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题栏
            Text(
                text = "排序方式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 升序 / 降序切换胶囊
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedAsc == 1,
                    onClick = {
                        selectedAsc = 1
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Default.ArrowUpward, null, Modifier.size(16.dp)) }
                ) {
                    Text("升序")
                }
                SegmentedButton(
                    selected = selectedAsc == 0,
                    onClick = {
                        selectedAsc = 0
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Default.ArrowDownward, null, Modifier.size(16.dp)) }
                ) {
                    Text("降序")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 排序项目列表 (可滚动)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 云端排序分组
                Text(
                    text = "云端排序 (同步网盘)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                )

                cloudTypes.forEach { type ->
                    OrderTypeItem(
                        type = type,
                        isSelected = selectedType == type,
                        onClick = { selectedType = type }
                    )
                }

                if (localTypes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(4.dp))

                    // 本地排序分组
                    Text(
                        text = "本地排序 (仅当前视图)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                    )

                    localTypes.forEach { type ->
                        OrderTypeItem(
                            type = type,
                            isSelected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                    }
                }
            }

            // 确认按钮
            Button(
                onClick = {
                    enter(OrderBean(type = selectedType.rawValue, asc = selectedAsc))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("确定")
            }
        }
    }
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
        onDismissRequest = {},// 外部事件不再触发关闭；也可保留为 onDismiss
        properties = DialogProperties(
            dismissOnClickOutside = false, // 点击弹窗外部（旁边遮罩）不关闭
            dismissOnBackPress = false      // 按系统返回键/侧滑手势不关闭，确保只能点击确认或取消
        ),
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
