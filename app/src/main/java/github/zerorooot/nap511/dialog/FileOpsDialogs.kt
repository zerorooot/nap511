package github.zerorooot.nap511.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.ForceOpenType
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
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
            Text(text = "把文件 '$fileName' 强行打开为", style = MaterialTheme.typography.titleLarge)
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
fun RenameFileDialog(name: String, enter: (String?) -> Unit) {
    val position by DataStoreUtil.getDataFlow(ConfigKeyUtil.POSITION_AFTER_AT, false)
        .collectAsStateWithLifecycle(initialValue = false)
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
