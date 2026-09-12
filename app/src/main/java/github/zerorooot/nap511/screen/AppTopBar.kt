package github.zerorooot.nap511.screen

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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.TopAppBarDefaults
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
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

enum class TopBarAction(
    override val label: String,
    override val icon: ImageVector? = null
) : AppBarAction {
    BACK("返回", Icons.AutoMirrored.Rounded.ArrowBack),
    DRAWER_MENU("抽屉菜单", Icons.Rounded.Menu),
    SEARCH("搜索", Icons.Rounded.Search),

    SELECT_UP("向上选", Icons.Default.ArrowUpward),
    SELECT_DOWN("向下选", Icons.Default.ArrowDownward),
    CUT("剪切", Icons.Default.ContentCut),
    DELETE("删除", Icons.Default.Delete),
    SELECT_REVERSE("反选", Icons.Default.SelectAll),
    UNZIP_ALL("解压", Icons.Default.Cloud),

    CLEAR_ALL_RECYCLE("清空所有文件", Icons.Default.DeleteForever)
}

@Composable
fun AppTopBarNormal(title: String, onClick: (AppBarAction) -> Unit) {
    BaseTopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            TopAppBarActionButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                description = "navigationIcon"
            ) {
                onClick.invoke(TopBarAction.BACK)
            }
        },
        actions = {
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

@Composable
fun AppTopBarMultiple(
    title: String,
    isExpandedScreen: Boolean = false,
    onClick: (AppBarAction) -> Unit
) {
    BaseTopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            IconButton(onClick = { onClick.invoke(TopBarAction.BACK) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "navigationIcon"
                )
            }
        },
        actions = {
            if (isExpandedScreen) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    TopAppBarActionTextButton(
                        imageVector = Icons.Default.ArrowUpward,
                        label = TopBarAction.SELECT_UP.label,
                        onClick = { onClick.invoke(TopBarAction.SELECT_UP) }
                    )
                    TopAppBarActionTextButton(
                        imageVector = Icons.Default.ArrowDownward,
                        label = TopBarAction.SELECT_DOWN.label,
                        onClick = { onClick.invoke(TopBarAction.SELECT_DOWN) }
                    )
                    TopAppBarActionTextButton(
                        imageVector = Icons.Default.ContentCut,
                        label = TopBarAction.CUT.label,
                        onClick = { onClick.invoke(TopBarAction.CUT) }
                    )
                    TopAppBarActionTextButton(
                        imageVector = Icons.Default.Delete,
                        label = TopBarAction.DELETE.label,
                        onClick = { onClick.invoke(TopBarAction.DELETE) }
                    )
                    TopAppBarActionTextButton(
                        imageVector = Icons.Default.SelectAll,
                        label = TopBarAction.SELECT_REVERSE.label,
                        onClick = { onClick.invoke(TopBarAction.SELECT_REVERSE) }
                    )
                    TopAppBarActionTextButton(
                        imageVector = Icons.Default.Cloud,
                        label = TopBarAction.UNZIP_ALL.label,
                        onClick = { onClick.invoke(TopBarAction.UNZIP_ALL) }
                    )
                }
            } else {
                TopAppBarActionButton(
                    Icons.Default.ArrowUpward,
                    description = "up"
                ) {
                    onClick.invoke(TopBarAction.SELECT_UP)
                }
                TopAppBarActionButton(
                    Icons.Default.ArrowDownward,
                    description = "down"
                ) {
                    onClick.invoke(TopBarAction.SELECT_DOWN)
                }
                TopAppBarActionButton(
                    Icons.Default.ContentCut,
                    description = "Cut"
                ) {
                    onClick.invoke(TopBarAction.CUT)
                }
                TopAppBarActionButton(
                    Icons.Default.Delete,
                    description = "delete"
                ) {
                    onClick.invoke(TopBarAction.DELETE)
                }
                TopAppBarActionButton(
                    Icons.Default.SelectAll,
                    description = "ic_baseline_select_reverse_24"
                ) {
                    onClick.invoke(TopBarAction.SELECT_REVERSE)
                }
                TopAppBarActionButton(
                    Icons.Default.Cloud,
                    description = "unzip file"
                ) {
                    onClick.invoke(TopBarAction.UNZIP_ALL)
                }
            }
        }
    )
}

@Composable
private fun TopAppBarActionTextButton(
    imageVector: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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

@Composable
fun AppTopBarOfflineFile(title: String, onClick: (AppBarAction) -> Unit) {
    BaseTopAppBar(
        title = { Text(text = title) },
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

@Composable
fun AppTopBarLogScreen(title: String, onClick: (AppBarAction) -> Unit) {
    BaseTopAppBar(
        title = { Text(text = title) },
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

@Composable
fun AppTopBarRepeatFile(title: String, onClick: (AppBarAction) -> Unit) {
    BaseTopAppBar(
        title = { Text(text = title) },
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

@Composable
fun AppTopBarRecycle(title: String, onClick: (AppBarAction) -> Unit) {
    BaseTopAppBar(
        title = { Text(text = title) },
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
