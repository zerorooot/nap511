package github.zerorooot.nap511.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileInfo
import github.zerorooot.nap511.bean.InfoItem
import github.zerorooot.nap511.bean.InfoSection
import github.zerorooot.nap511.bean.OfflineTask
import github.zerorooot.nap511.bean.PathsBean

@Composable
fun FileInfoDialog(
    fileBean: FileBean,
    fileInfo: FileInfo,
    fileInfoClick: (String?) -> Unit
) {
    val icon = fileBean.fileIco
    val sections = mutableListOf<InfoSection>()

    val baseItems = mutableListOf<InfoItem>()
    baseItems.add(InfoItem("类型", if (fileBean.isFolder) "文件夹" else "文件"))
    if (fileBean.isFolder) {
        baseItems.add(
            InfoItem(
                "包含内容", "${fileInfo.count} 个文件, ${fileInfo.folderCount} 个文件夹"
            )
        )
    }
    baseItems.add(
        InfoItem(
            "总大小", fileInfo.size.ifEmpty { fileBean.sizeString.ifEmpty { "0 B" } })
    )
    sections.add(InfoSection(title = "基础信息", items = baseItems))

    val locationItems = mutableListOf(
        InfoItem(
            label = "文件路径",
            value = "",
            customContent = {
                BreadcrumbPath(
                    paths = fileInfo.paths,
                    onPathClick = { categoryId ->
                        fileInfoClick(categoryId)
                        fileInfoClick.invoke(null)
                    }
                )
            }
        ),
        InfoItem("提取码", fileBean.pickCode.ifEmpty { "无" }),
    )
    if (fileBean.sha1 != "") {
        locationItems.add(InfoItem("sha1", fileBean.sha1))
    }

    sections.add(InfoSection(title = "位置与共享", items = locationItems))

    val timeItems = listOf(
        InfoItem("创建时间", fileBean.createTimeString.ifEmpty { "未知" }),
        InfoItem("修改时间", fileBean.modifiedTimeString.ifEmpty { "未知" })
    )
    sections.add(InfoSection(title = "时间信息", items = timeItems))

    BaseDetailDialog(
        title = fileBean.name,
        icon = icon,
        sections = sections,
        onDismissRequest = { fileInfoClick.invoke(null) }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreadcrumbPath(
    paths: List<PathsBean>,
    onPathClick: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalArrangement = Arrangement.Center
    ) {
        if (paths.isEmpty()) {
            Text(
                text = "根目录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onPathClick("0") }
            )
        } else {
            paths.forEachIndexed { index, path ->
                Text(
                    text = path.fileName.ifEmpty { "根目录" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onPathClick(path.fileId) }
                )

                if (index < paths.size - 1) {
                    Text(
                        text = " > ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun BaseDetailDialog(
    title: String, icon: Int, sections: List<InfoSection>, onDismissRequest: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismissRequest, title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(icon),
                modifier = Modifier.size(28.dp),
                contentScale = ContentScale.Fit,
                contentDescription = "",
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.titleMedium.lineHeight
            )
        }
    }, text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            sections.forEachIndexed { index, section ->
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp)
                )

                section.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))
                        Box(
                            modifier = Modifier.weight(2f),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            if (item.customContent != null) {
                                item.customContent.invoke()
                            } else {
                                Text(
                                    text = item.value.ifEmpty { "-" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.End,
                                    minLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                if (index < sections.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = onDismissRequest) {
            Text("关闭")
        }
    })
}

@Composable
fun OfflineFileInfoDialog(
    isOpen: Boolean,
    task: OfflineTask?,
    onDismissRequest: () -> Unit
) {
    if (isOpen && task != null) {
        OfflineTaskDialog(task, onDismissRequest = onDismissRequest)
    }
}

@Composable
fun OfflineTaskDialog(
    task: OfflineTask, onDismissRequest: () -> Unit
) {
    val sections = listOf(
        InfoSection(
            title = "任务信息", items = listOf(
                InfoItem("状态", task.percentString),
                InfoItem("总大小", task.sizeString),
                InfoItem("下载进度", "${task.percentDone}%")
            )
        ), InfoSection(
            title = "链接与哈希", items = listOf(
                InfoItem("哈希值", task.infoHash),
                InfoItem("链接", task.url)
            )
        ), InfoSection(
            title = "时间信息", items = listOf(
                InfoItem("添加时间", task.timeString)
            )
        )
    )

    BaseDetailDialog(
        title = task.name,
        icon = if (task.fileId == "") R.drawable.other else R.drawable.folder,
        sections = sections,
        onDismissRequest = onDismissRequest
    )
}
