package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.bean.TorrentFileListWeb
import github.zerorooot.nap511.dialog.DynamicEllipsizedTextView


/**
 * 种子文件列表项（支持勾选状态指示、路径自适应省略与文件大小展示）。
 */
@Composable
fun TorrentFileCellItem(
    item: TorrentFileListWeb,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                } else {
                    Color.Transparent
                }
            )
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Checkbox
            )
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Icon(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(22.dp),
            imageVector = if (isSelected) {
                Icons.Outlined.CheckBox
            } else {
                Icons.Outlined.CheckBoxOutlineBlank
            },
            contentDescription = null,
            tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        DynamicEllipsizedTextView(
            text = item.path,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = item.sizeString,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}