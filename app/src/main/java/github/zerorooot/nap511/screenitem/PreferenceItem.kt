package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import github.zerorooot.nap511.screen.BaseDialog

// 1. 普通点击项（如：重启应用、离线下载验证）
@Composable
fun PreferenceItem(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = summary?.let { { Text(text = it) } },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}

// 2. 开关选项（如：屏幕自动旋转、日志记录）
@Composable
fun SwitchPreferenceItem(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = summary?.let { { Text(text = it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

// 3. 弹窗输入选项（如：修改 uid、password、aria2地址等）
@Composable
fun EditTextPreferenceItem(
    title: String,
    summary: String,
    value: String,
    label: String = title, // 默认使用 title 作为 输入框 label
    isNumber: Boolean = false,
    enabled: Boolean = true,
    onValueSave: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
// 禁用状态下的透明度，符合 Material 3 规范 (0.38f)
    val disabledAlpha = 0.38f

    val headlineColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
    }

    val supportingColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
    }

    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = summary) },
        colors = ListItemDefaults.colors(
            headlineColor = headlineColor,
            supportingColor = supportingColor
        ),
        modifier = Modifier.clickable(enabled = enabled) {
            showDialog = true
        }
    )

    if (showDialog) {
        BaseDialog(
            title = title,
            label = label,
            context = value,
            keyboardOptions = if (isNumber) {
                KeyboardOptions(keyboardType = KeyboardType.Number)
            } else {
                KeyboardOptions.Default
            },
            enter = { result ->
                showDialog = false // 无论确认还是取消，都关闭 Dialog
                if (result != null) {
                    onValueSave(result) // 仅在用户确认输入时保存
                }
            }
        )
    }
}