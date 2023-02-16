package github.zerorooot.nap511.screen

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import github.zerorooot.nap511.R
import github.zerorooot.nap511.ui.theme.Pink80
import github.zerorooot.nap511.ui.theme.Purple40
import github.zerorooot.nap511.ui.theme.Purple80

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarNormal(onClick: (name: String) -> Unit) {
//    val contextForToast = LocalContext.current.applicationContext
    TopAppBar(
        title = {
            Text(text = "nap511")
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
            AppTopBarDropdownMenu(onClick = { _, index ->
                onClick.invoke(index.toString())
            })
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarMultiple(onClick: (String) -> Unit) {
    TopAppBar(
        title = {
            Text(text = "nap511")
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple80),
        navigationIcon = {
            IconButton(onClick = { onClick.invoke("back") }) {
                Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "navigationIcon")
            }
        },
        actions = {
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

@Preview
@Composable
private fun testa() {
//    AppTopBarNormal()
}

@Preview
@Composable
private fun test() {
//    AppTopBarMultiple()
}


@Composable
private fun TopAppBarActionButton(
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

