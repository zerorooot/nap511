package github.zerorooot.nap511.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.terminal.engine.GlobMatcher
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings

/**
 * 批量重命名弹窗组件
 *
 * 核心功能：
 * 1. 第一行：通配符筛选（默认预填 `*`，清空时自动降级回退为 `*`，支持 `*.zip` 等）+ 查找正则（支持任意有效正则表达式）；
 * 2. 第二行：替换为（支持替换内容，原生支持 `$1`、`$2` 捕获组引用）；
 * 3. 循环外预编译正则，循环内仅处理替换操作，防范 Compose 重组过程中的高频重复编译；
 * 4. 实时安全演算：打字期间全程 runCatching 防御未闭合正则与非法反向引用，绝不导致 Compose 重组闪退；
 * 5. 下方提供带滚动条的实时预览列表，直观展示每个被匹配文件的变更前后对比；
 * 6. 统一使用 file.fileId.ifEmpty { file.categoryId } 作为映射主键，完美支持普通文件与文件夹混合批量重命名。
 */
@Composable
fun BatchRenameDialog(
    targetFiles: List<FileBean>,
    onDismissRequest: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit
) {
    var wildcard by remember { mutableStateOf("*") }
    var findPattern by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    // 实时计算变更项列表（内置异常安全保护与单次预编译）
    val previewItems = remember(wildcard, findPattern, replacement, targetFiles) {
        if (findPattern.isBlank()) return@remember emptyList()

        // 1. 通配符模式解析，输入空时安全回退至 "*"
        val effectiveWildcard = wildcard.ifBlank { "*" }
        val globRegex = runCatching { GlobMatcher.globToRegex(effectiveWildcard) }.getOrNull()

        // 2. 正则查找模式在循环外预编译，语法不合法时直接安全返回空列表（输入过程中平稳防崩）
        val regex = runCatching { Regex(findPattern) }.getOrNull() ?: return@remember emptyList()

        // 3. 遍历目标文件，执行通配符过滤与正则安全替换
        targetFiles.mapNotNull { file ->
            // 通配符匹配筛选
            if (globRegex != null && !globRegex.matches(file.name)) return@mapNotNull null

            // 安全替换（拦截反向引用越界或格式错误等 runtime 异常）
            val newName = runCatching {
                regex.replace(file.name, replacement)
            }.getOrDefault(file.name)

            // 过滤空白文件名以及未产生实质变更的文件
            if (newName.isNotBlank() && newName != file.name) {
                file to newName
            } else {
                null
            }
        }
    }

    val renameMap = remember(previewItems) {
        // 关键：提取真实唯一标识
        /**
         * fid与cid已经在 [github.zerorooot.nap511.util.formatFileBeanList] 中统一了，所以统一使用fid
         */
        previewItems.associate { (file, newName) ->
            file.fileId to newName
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "批量重命名 (已选 ${targetFiles.size} 项)") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = wildcard,
                        onValueChange = { wildcard = it },
                        label = { Text("通配符") },
                        placeholder = { Text("*") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = findPattern,
                        onValueChange = { findPattern = it },
                        label = { Text("查找正则") },
                        placeholder = { Text("如 \\(") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("替换为") },
                    placeholder = { Text("支持 $1 捕获组") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    if (previewItems.isEmpty()) {
                        Text(
                            text = "未匹配到任何文件或未产生名称变更",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumnScrollbar(
                            state = listState,
                            settings = ScrollbarSettings.Default.copy(
                                thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
                            )
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxWidth(), state = listState) {
                                items(
                                    items = previewItems,
                                    key = { (file, _) -> file.fileId }
                                ) { (file, newName) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = " ➔ ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = newName,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    XLog.d("BatchRenameDialog: 确认重命名 ${renameMap.size} 项")
                    onConfirm(renameMap)
                },
                enabled = previewItems.isNotEmpty()
            ) {
                Text("确认 (${previewItems.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        }
    )
}
