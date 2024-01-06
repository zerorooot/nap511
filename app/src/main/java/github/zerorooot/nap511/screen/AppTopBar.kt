package github.zerorooot.nap511.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import github.zerorooot.nap511.R
import github.zerorooot.nap511.ui.theme.Purple80

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarNormal(title: String, onClick: (name: String) -> Unit) {
//    val contextForToast = LocalContext.current.applicationContext
    TopAppBar(
        title = {
            Text(text = title)
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple80),
        navigationIcon = {
            TopAppBarActionButton(
                imageVector = Icons.Rounded.ArrowBack,
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
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple80),
        navigationIcon = {
            IconButton(onClick = { onClick.invoke("back") }) {
                Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "navigationIcon")
            }
        },
        actions = {
            TopAppBarActionButton(
                painter = painterResource(id = R.drawable.ic_baseline_arrow_upward_24),
                description = "up"
            ) {
                onClick.invoke("selectToUp")
            }
            TopAppBarActionButton(
                painter = painterResource(id = R.drawable.ic_baseline_arrow_downward_24),
                description = "down"
            ) {
                onClick.invoke("selectToDown")
            }
            // cut icon
            TopAppBarActionButton(
                painter = painterResource(id = R.drawable.ic_baseline_content_cut_24),
                description = "Cut"
            ) {
                onClick.invoke("cut")
            }

            TopAppBarActionButton(
                painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                description = "delete"
            ) {
                onClick.invoke("delete")
            }
            TopAppBarActionButton(
                painter = painterResource(id = R.drawable.ic_baseline_select_all_24),
                description = "ic_baseline_select_all_24"
            ) {
                onClick.invoke("selectAll")
            }

            TopAppBarActionButton(
                painter = painterResource(id = R.drawable.ic_baseline_select_reverse_24),
                description = "ic_baseline_select_reverse_24"
            ) {
                onClick.invoke("selectReverse")
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
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple80),
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
fun AppTopBarRecycle(title: String, onClick: (name: String) -> Unit) {
    TopAppBar(
        title = {
            Text(text = title)
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple80),
        navigationIcon = {
            TopAppBarActionButton(
                imageVector = Icons.Rounded.Menu,
                description = "navigationIcon"
            ) {
                onClick.invoke("ModalNavigationDrawerMenu")
            }
        },
        actions = {
            RecycleAppTopBarDropdownMenu(onClick = { itemValue, _ ->
                onClick.invoke(itemValue)
            })
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

