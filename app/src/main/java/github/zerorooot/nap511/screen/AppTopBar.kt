package github.zerorooot.nap511.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import github.zerorooot.nap511.ui.theme.Typography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarNormal(title: String, onClick: (name: String) -> Unit) {
//    val contextForToast = LocalContext.current.applicationContext
    TopAppBar(
        title = {
            Text(text = title)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        navigationIcon = {
            TopAppBarActionButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                description = "navigationIcon"
            ) {
                onClick.invoke("back")
            }
        },
        actions = {
            // search icon
            TopAppBarActionButton(
                imageVector = Icons.Rounded.Search,
                description = "Search"
            ) {
                onClick.invoke("search")
            }
            FileAppTopBarDropdownMenu(onClick = { itemValue, _ ->
                onClick.invoke(itemValue)
            })
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarMultiple(title: String, onClick: (String) -> Unit) {
    TopAppBar(
        title = {
            Text(text = title)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        navigationIcon = {
            IconButton(onClick = { onClick.invoke("back") }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "navigationIcon"
                )
            }
        },
        actions = {
            TopAppBarActionButton(
                Icons.Default.ArrowUpward,
                description = "up"
            ) {
                onClick.invoke("selectToUp")
            }
            TopAppBarActionButton(
                Icons.Default.ArrowDownward,
                description = "down"
            ) {
                onClick.invoke("selectToDown")
            }
            // cut icon
            TopAppBarActionButton(
                Icons.Default.ContentCut,
                description = "Cut"
            ) {
                onClick.invoke("cut")
            }

            TopAppBarActionButton(
                Icons.Default.Delete,
                description = "delete"
            ) {
                onClick.invoke("delete")
            }
//            TopAppBarActionButton(
//                painter = painterResource(id = R.drawable.ic_baseline_select_all_24),
//                description = "ic_baseline_select_all_24"
//            ) {
//                onClick.invoke("selectAll")
//            }
            TopAppBarActionButton(
                Icons.Default.SelectAll,
                description = "ic_baseline_select_reverse_24"
            ) {
                onClick.invoke("selectReverse")
            }
            //R.drawable.baseline_cloud_download_24
            TopAppBarActionButton(
                Icons.Default.Cloud,
                description = "unzip file"
            ) {
                onClick.invoke("unzipAllFile")
            }
//            TopAppBarActionButton(
//                painter = painterResource(id = R.drawable.baseline_close_24),
//                description = "ic_baseline_select_all_24"
//            ) {
//                onClick.invoke("close")
//            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarOfflineFile(title: String, onClick: (name: String) -> Unit) {
//    val contextForToast = LocalContext.current.applicationContext
    TopAppBar(
        title = {
            Text(text = title)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        navigationIcon = {
            TopAppBarActionButton(
                imageVector = Icons.Rounded.Menu,
                description = "navigationIcon"
            ) {
                onClick.invoke("ModalNavigationDrawerMenu")
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
fun AppTopBarLogScreen(title: String, onClick: (name: String) -> Unit) {
//    val contextForToast = LocalContext.current.applicationContext
    TopAppBar(
        title = {
            Text(text = title)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        navigationIcon = {
            TopAppBarActionButton(
                imageVector = Icons.Rounded.Menu,
                description = "navigationIcon"
            ) {
                onClick.invoke("ModalNavigationDrawerMenu")
            }
        },
        actions = {
            TopAppBarActionButton(
                imageVector = Icons.Default.Search,
                description = "搜索"
            ) {
                onClick.invoke("搜索")
            }
            LogScreenTopBarDropdownMenu(onClick = { itemValue, _ ->
                onClick.invoke(itemValue)
            })
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarRepeatFile(title: String, onClick: (name: String) -> Unit) {
    TopAppBar(
        title = {
            Text(text = title)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        navigationIcon = {
            TopAppBarActionButton(
                imageVector = Icons.Rounded.Menu,
                description = "navigationIcon"
            ) {
                onClick.invoke("ModalNavigationDrawerMenu")
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
fun AppTopBarRecycle(title: String, onClick: (name: String) -> Unit) {
    TopAppBar(
        title = {
            Text(text = title)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        navigationIcon = {
            TopAppBarActionButton(
                imageVector = Icons.Rounded.Menu,
                description = "navigationIcon"
            ) {
                onClick.invoke("ModalNavigationDrawerMenu")
            }
        },
        actions = {
            IconButton(onClick = { onClick.invoke("清空所有文件") }) {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = "清空所有文件"
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
    TopAppBar(
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
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
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
    TopAppBar(
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
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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

