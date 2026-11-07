package github.zerorooot.nap511.screen.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
        colors = colors,
    )
}

enum class TopBarAction(
    override val label: String,
    override val icon: ImageVector,
    override val isDestructive: Boolean = false,
    val shortLabel: String = label // 默认为原 label
) : AppBarAction {
    BACK("返回", Icons.AutoMirrored.Rounded.ArrowBack),
    DRAWER_MENU("抽屉菜单", Icons.Rounded.Menu),
    SEARCH("搜索", Icons.Rounded.Search),
    TOP("文件置顶", Icons.Default.PushPin, shortLabel = "置顶"),
    STAR("文件标星", Icons.Default.StarOutline, shortLabel = "标星"),
    STAR_FILE_SCREEN("星标文件", Icons.Default.StarOutline),
    SELECT_UP("向上全选", Icons.Default.ArrowUpward, shortLabel = "上选"),
    SELECT_DOWN("向下全选", Icons.Default.ArrowDownward, shortLabel = "下选"),
    SELECT_REVERSE("反向全选", Icons.Default.SwapVert, shortLabel = "反选"),
    CUT("剪切文件", Icons.Default.ContentCut, shortLabel = "剪切"),
    DELETE("删除文件", Icons.Default.Delete, shortLabel = "删除"),
    UNZIP_ALL("解压文件", Icons.Default.FolderZip, shortLabel = "解压"),
    BATCH_RENAME("重新命名", Icons.Default.Edit, shortLabel = "命名"),
    TOGGLE_SELECT_ALL("全选/取消全选", Icons.Default.SelectAll, shortLabel = "全选"),

    CLEAR_ALL_RECYCLE("清空所有文件", Icons.Default.DeleteForever)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarNormal(
    title: String,
    imageVector: ImageVector,
    onClick: (AppBarAction) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    BaseTopAppBar(
        title = { Text(text = title) },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            TopAppBarActionButton(
                imageVector = imageVector,
                description = "navigationIcon"
            ) {
                onClick.invoke(TopBarAction.BACK)
            }
        },
        actions = {
            TopAppBarActionButton(
                imageVector = Icons.Default.StarOutline,
                description = "Star"
            ) {
                onClick.invoke(TopBarAction.STAR_FILE_SCREEN)
            }
            TopAppBarActionButton(
                imageVector = Icons.Rounded.Search,
                description = "Search"
            ) {
                onClick.invoke(TopBarAction.SEARCH)
            }
            FileAppTopBarDropdownMenu(onClick = { itemValue, _ ->
                onClick.invoke(itemValue)
            })
        }
    )
}

private val landscapeActions = listOf(
    TopBarAction.TOGGLE_SELECT_ALL,
    TopBarAction.SELECT_REVERSE,
    TopBarAction.SELECT_UP,
    TopBarAction.SELECT_DOWN,
    TopBarAction.CUT,
    TopBarAction.BATCH_RENAME,
    TopBarAction.DELETE,
    TopBarAction.TOP,
    TopBarAction.STAR,
    TopBarAction.UNZIP_ALL
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarMultiple(
    isLandscape: Boolean = false,
    isAllSelected: Boolean = false,
    selectedCount: Int = 0,
    onClick: (AppBarAction) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    BaseTopAppBar(
        title = {
            val displayText = if (selectedCount > 0) "已选择 $selectedCount 项" else "未选择"
            Text(text = displayText)
        },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = { onClick(TopBarAction.BACK) }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "退出多选"
                )
            }
        },
        actions = {
            if (isLandscape) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    landscapeActions.forEach { action ->
                        val isToggle = action == TopBarAction.TOGGLE_SELECT_ALL
                        val actionIcon = if (isToggle && isAllSelected) {
                            Icons.Default.Deselect
                        } else action.icon
                        val actionLabel = if (isToggle) {
                            if (isAllSelected) "取消" else "全选" // 动态切换：保证严格 2 个字
                        } else {
                            action.shortLabel // 严格 2 个字
                        }
                        val isEnabled = isToggle ||
                                action == TopBarAction.SELECT_REVERSE ||
                                selectedCount > 0

                        TopAppBarActionTextButton(
                            imageVector = actionIcon,
                            label = actionLabel,
                            enabled = isEnabled,
                            onClick = { onClick(action) }
                        )
                    }
                }
            } else {
                // 1. 全选 / 取消全选动态按钮
                TopAppBarActionButton(
                    imageVector = if (isAllSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                    description = if (isAllSelected) "取消全选" else "全选",
                    onClick = { onClick(TopBarAction.TOGGLE_SELECT_ALL) }
                )
                // 2. 选择助手（反选、上选、下选）下拉菜单
                FileAppTopBarMultipleDropdownMenu(
                    onClick = { itemValue, _ ->
                        onClick.invoke(itemValue)
                    }
                )
            }
        }
    )
}

@Composable
private fun TopAppBarActionTextButton(
    imageVector: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = contentColor
        )
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = label,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarOfflineFile(
    title: String,
    onClick: (AppBarAction) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    BaseTopAppBar(
        title = { Text(text = title) },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            TopAppBarActionButton(
                imageVector = Icons.Rounded.Menu,
                description = "navigationIcon"
            ) {
                onClick.invoke(TopBarAction.DRAWER_MENU)
            }
        },
        actions = {
            OfflineFileAppTopBarDropdownMenu(onClick = { itemValue, _ ->
                onClick.invoke(itemValue)
            })
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarLogScreen(
    title: String,
    onClick: (AppBarAction) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    BaseTopAppBar(
        title = { Text(text = title) },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            TopAppBarActionButton(
                imageVector = Icons.Rounded.Menu,
                description = "navigationIcon"
            ) {
                onClick.invoke(TopBarAction.DRAWER_MENU)
            }
        },
        actions = {
            TopAppBarActionButton(
                imageVector = Icons.Default.Search,
                description = "搜索"
            ) {
                onClick.invoke(TopBarAction.SEARCH)
            }
            LogScreenTopBarDropdownMenu(onClick = { itemValue, _ ->
                onClick.invoke(itemValue)
            })
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarRepeatFile(
    title: String,
    onClick: (AppBarAction) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    BaseTopAppBar(
        title = { Text(text = title) },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            TopAppBarActionButton(
                imageVector = Icons.Rounded.Menu,
                description = "navigationIcon"
            ) {
                onClick.invoke(TopBarAction.DRAWER_MENU)
            }
        },
        actions = {
            RepeatFileTopBarDropdownMenu(onClick = { itemValue, _ ->
                onClick.invoke(itemValue)
            })
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarRecycle(
    title: String,
    onClick: (AppBarAction) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    BaseTopAppBar(
        title = { Text(text = title) },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            TopAppBarActionButton(
                imageVector = Icons.Rounded.Menu,
                description = "navigationIcon"
            ) {
                onClick.invoke(TopBarAction.DRAWER_MENU)
            }
        },
        actions = {
            IconButton(onClick = { onClick.invoke(TopBarAction.CLEAR_ALL_RECYCLE) }) {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = TopBarAction.CLEAR_ALL_RECYCLE.label
                )
            }
        }
    )
}

@Composable
fun TopAppBarActionButton(
    imageVector: ImageVector? = null,
    painter: Painter? = null,
    description: String,
    onClick: () -> Unit
) {
    IconButton(onClick = {
        onClick()
    }) {
        if (imageVector != null) {
            Icon(imageVector = imageVector, contentDescription = description)
        }
        if (painter != null) {
            Icon(painter = painter, contentDescription = description)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarTxtReaderNormal(
    title: String,
    currentEncoding: String,
    paragraphsCount: Int,
    onBackClick: () -> Unit,
    onSearchOpen: () -> Unit,
    onShareClick: () -> Unit,
    onEncodingClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseTopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = "编码: $currentEncoding | 行数: $paragraphsCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
        },
        actions = {
            IconButton(onClick = onSearchOpen) {
                Icon(Icons.Default.Search, contentDescription = "搜索")
            }
            IconButton(onClick = onShareClick) {
                Icon(Icons.Default.Share, contentDescription = "分享文本")
            }
            IconButton(onClick = onEncodingClick) {
                Icon(Icons.Default.Translate, contentDescription = "切换编码")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "阅读设置")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarSearch(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    matchCount: Int,
    currentMatchIndex: Int,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String = "搜索...",
    focusRequester: FocusRequester
) {
    BaseTopAppBar(
        modifier = modifier,
        title = {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(placeholderText) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        navigationIcon = {
            IconButton(onClick = onCloseSearch) {
                Icon(Icons.Default.Close, contentDescription = "关闭搜索")
            }
        },
        actions = {
            val countText = if (matchCount == 0) "0/0" else "${currentMatchIndex + 1}/$matchCount"
            Text(
                text = countText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(
                onClick = onPrevMatch,
                enabled = matchCount > 0
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一个")
            }
            IconButton(
                onClick = onNextMatch,
                enabled = matchCount > 0
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一个")
            }
        }
    )
}

@Composable
fun TopAppBarTxtReaderSearch(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    matchCount: Int,
    currentMatchIndex: Int,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester
) {
    TopAppBarSearch(
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onCloseSearch = onCloseSearch,
        matchCount = matchCount,
        currentMatchIndex = currentMatchIndex,
        onPrevMatch = onPrevMatch,
        onNextMatch = onNextMatch,
        modifier = modifier,
        placeholderText = "搜索文本...",
        focusRequester = focusRequester
    )
}
