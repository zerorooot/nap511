package github.zerorooot.nap511.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.ZipBean
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.closeUnzipAllFileDialog
import github.zerorooot.nap511.viewmodel.closeUnzipDialog
import github.zerorooot.nap511.viewmodel.getZipListFile
import github.zerorooot.nap511.viewmodel.unzipFile
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings

/**
 * 云解压密码输入对话框
 */
@Composable
fun UnzipPassword(fileBean: FileBean, enter: (String?) -> Unit) {
    BaseDialog(
        title = "云解压-${fileBean.name}",
        label = "请输入密码",
        dismissButtonText = "取消",
        enter = enter
    )
}

/**
 * 批量云解压密码输入对话框
 */
@Composable
fun UnzipAllFile(
    fileViewModel: FileViewModel
) {
    BaseDialog("请输入解压密码", "如无加密，为空即可") { pwd ->
        if (pwd == null) {
            fileViewModel.closeUnzipAllFileDialog()
            return@BaseDialog
        }

        val currentCid = fileViewModel.currentCid

        fileViewModel.closeUnzipAllFileDialog()

        val message =
            fileViewModel.fileBeanList.filter { i -> i.isSelect && i.fileIco == R.drawable.zip }
                .takeIf { it.isNotEmpty() }?.let {
                    fileViewModel.unzipFile(it, currentCid, pwd)
                    "后台解压中......"
                } ?: run {
                "请选中压缩包解压！"
            }
        App.instance.toast(message)

        fileViewModel.clearSelection()
    }
}

/**
 * 云解压主对话框容器（ViewModel 业务装配层）
 * 负责状态收集与业务分发，解耦具体的 UI 布局逻辑。
 */
@Composable
fun UnzipDialog(fileViewModel: FileViewModel) {
    val dialogState by fileViewModel.dialogState.collectAsStateWithLifecycle()
    val fileBean = dialogState.targetFileBean ?: return
    val zipBeanList = dialogState.unzipBeanList

    LaunchedEffect(Unit) {
        fileViewModel.setRefreshingStatus(false)
    }

    // 解析当前路径层级片段列表
    val pathSegments = remember(zipBeanList.pathString) {
        if (zipBeanList.pathString.isEmpty()) {
            listOf("文件")
        } else {
            zipBeanList.pathString.split("/")
        }
    }
    val canGoUp = pathSegments.size > 1

    /**
     * 根据面包屑目标层级索引发起跳转
     */
    fun navigateToBreadcrumb(targetIndex: Int) {
        if (targetIndex >= pathSegments.size - 1) return
        val (targetFileName, targetPaths) = if (targetIndex <= 0) {
            "" to "文件"
        } else {
            pathSegments[targetIndex] to pathSegments.subList(0, targetIndex).joinToString(separator = "/")
        }
        fileViewModel.getZipListFile(fileBean, targetFileName, paths = targetPaths)
    }

    UnzipScreen(
        zipBeanList = zipBeanList,
        fileName = fileBean.name,
        canGoUp = canGoUp,
        pathSegments = pathSegments,
        onDismiss = { fileViewModel.closeUnzipDialog() },
        onNavigateUp = { navigateToBreadcrumb(pathSegments.size - 2) },
        onBreadcrumbClick = { index -> navigateToBreadcrumb(index) },
        onFolderClick = { folderName ->
            fileViewModel.getZipListFile(
                fileBean, folderName, paths = zipBeanList.pathString
            )
        },
        onUnzipAll = {
            fileViewModel.unzipFile(fileBean)
            fileViewModel.closeUnzipDialog()
        }
    )
}

/**
 * 云解压界面展示组件（纯 UI 层，高内聚低耦合）
 * 遵循 Material Design 3 规范重构：
 * 1. 规范化弹窗最大宽度与高度约束；
 * 2. 顶部主标题与卡片式面包屑导航条分离；
 * 3. 交互式面包屑与一键返回上一级；
 * 4. 规范化 M3 ListItem 列表项、平滑滚动条与空目录状态；
 * 5. 规范化底部主次操作按钮（确认与取消）。
 */
@Composable
fun UnzipScreen(
    zipBeanList: ZipBeanList,
    fileName: String,
    canGoUp: Boolean,
    pathSegments: List<String>,
    onDismiss: () -> Unit,
    onNavigateUp: () -> Unit,
    onBreadcrumbClick: (index: Int) -> Unit,
    onFolderClick: (folderName: String) -> Unit,
    onUnzipAll: () -> Unit
) {
    val listState = rememberLazyListState()

    // 适配屏幕高度，限制弹窗最大高度为窗口高度的 70%
    val maxDialogHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.70f
    }

    // 目录变更时自动回滚到列表首项
    LaunchedEffect(zipBeanList.pathString) {
        if (zipBeanList.list.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    AlertDialog(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
        onDismissRequest = onDismiss,
        title = {
            UnzipDialogTitle(fileName = fileName)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
            ) {
                // 1. 顶部路径导航与交互式面包屑栏
                UnzipPathBreadcrumbBar(
                    pathSegments = pathSegments,
                    itemCount = zipBeanList.list.size,
                    canGoUp = canGoUp,
                    onNavigateUp = onNavigateUp,
                    onBreadcrumbClick = onBreadcrumbClick
                )

                // 2. 压缩包内文件列表与状态视口
                if (zipBeanList.list.isEmpty()) {
                    UnzipEmptyState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LazyColumnScrollbar(
                            state = listState,
                            settings = ScrollbarSettings.Default.copy(
                                thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
                            )
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                itemsIndexed(
                                    items = zipBeanList.list,
                                    key = { index, item -> "${index}_${item.fileName}" }
                                ) { _, item ->
                                    UnzipFileListItem(
                                        item = item,
                                        onClick = { onFolderClick(item.fileName) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "关闭")
            }
        },
        confirmButton = {
            Button(
                onClick = onUnzipAll,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "解压到当前文件夹")
            }
        }
    )
}

/**
 * 历史调用兼容重载（保留 Pair<Boolean, String> 回调协议）
 */
@Composable
fun UnzipScreen(
    zipBeanList: ZipBeanList,
    fileName: String,
    enter: (Pair<Boolean, String>) -> Unit
) {
    val pathSegments = remember(zipBeanList.pathString) {
        if (zipBeanList.pathString.isEmpty()) {
            listOf("文件")
        } else {
            zipBeanList.pathString.split("/")
        }
    }
    val canGoUp = pathSegments.size > 1

    UnzipScreen(
        zipBeanList = zipBeanList,
        fileName = fileName,
        canGoUp = canGoUp,
        pathSegments = pathSegments,
        onDismiss = { enter(Pair(true, "exit")) },
        onNavigateUp = { enter(Pair(true, "up")) },
        onBreadcrumbClick = { _ -> enter(Pair(true, "up")) },
        onFolderClick = { folderName -> enter(Pair(false, folderName)) },
        onUnzipAll = { enter(Pair(true, "unzipAll")) }
    )
}

/**
 * 对话框标题区组件（包含 M3 主题色图标徽章与单行省略文本）
 */
@Composable
private fun UnzipDialogTitle(fileName: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "云解压 - $fileName",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 顶部导航与可交互式面包屑栏组件
 * 集成「返回上一级」IconButton、横向可滑动面包屑标签以及当前目录项数角标。
 */
@Composable
private fun UnzipPathBreadcrumbBar(
    pathSegments: List<String>,
    itemCount: Int,
    canGoUp: Boolean,
    onNavigateUp: () -> Unit,
    onBreadcrumbClick: (index: Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // 返回上一级按钮（根目录时置灰禁用）
            IconButton(
                onClick = onNavigateUp,
                enabled = canGoUp,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回上一级",
                    tint = if (canGoUp) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 横向滑动面包屑路径
            val scrollState = rememberScrollState()
            LaunchedEffect(pathSegments.size) {
                scrollState.scrollTo(scrollState.maxValue)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                pathSegments.forEachIndexed { index, rawSegment ->
                    val isCurrent = index == pathSegments.lastIndex
                    val displayName = if (index == 0 && (rawSegment == "文件" || rawSegment.isEmpty())) {
                        "根目录"
                    } else {
                        rawSegment
                    }

                    if (index > 0) {
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        } else {
                            Color.Transparent
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (!isCurrent) {
                                    Modifier.clickable { onBreadcrumbClick(index) }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Text(
                            text = displayName,
                            style = if (isCurrent) {
                                MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.labelMedium
                            },
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 当前目录项数统计胶囊
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = "共 $itemCount 项",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * 列表项单元组件（M3 ListItem 设计规范）
 * 包含 40dp 浅色圆角图标底座、主标题、以圆点分隔的副标题元信息，以及文件夹展开指示。
 */
@Composable
private fun UnzipFileListItem(
    item: ZipBean,
    onClick: () -> Unit
) {
    val isFolder = item.fileCategory == 0 || item.fileIco == R.drawable.folder

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = isFolder, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // 40dp 圆角图标底座
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isFolder) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(item.fileIco),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 文件名与元信息
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))

            val subtitle = if (isFolder) {
                "文件夹"
            } else {
                val size = item.sizeString.trim()
                val time = item.timeString.trim()
                when {
                    size.isNotEmpty() && time.isNotEmpty() -> "$size  •  $time"
                    size.isNotEmpty() -> size
                    time.isNotEmpty() -> time
                    else -> "-"
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 文件夹右侧进入箭头指示
        if (isFolder) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "进入文件夹",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 列表空状态占位组件
 */
@Composable
private fun UnzipEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "此目录下没有文件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
