package github.zerorooot.nap511.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.R

@Composable
private fun MyDropdownMenu(
    listItems: List<String>,
    modifier: Modifier,
    icon: @Composable () -> Unit,
    onClick: (String, Int) -> Unit
) {
//    val disabledItem = 2
//    val contextForToast = LocalContext.current.applicationContext

    // state of the menu
    var expanded by remember {
        mutableStateOf(false)
    }

    Box(
//        contentAlignment = Alignment.Center,
//        modifier = Modifier.fillMaxHeight()
        modifier = modifier
//        Modifier.wrapContentSize(Alignment.TopEnd)
    ) {
        IconButton(
            content = icon,
            onClick = {
                expanded = true
            }
        )
        // drop down menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {
            // adding items
            listItems.forEachIndexed { itemIndex, itemValue ->
                DropdownMenuItem(
                    onClick = {
                        onClick.invoke(itemValue, itemIndex)
                        expanded = false
                    },
//                    enabled = (itemIndex == disabledItem),
                    text = { Text(text = itemValue) }
                )
            }
        }
    }
}

@Composable
fun FileMoreMenu(onClick: (String, Int) -> Unit) {
    val listOf = stringArrayResource(id = R.array.fileMenu).toList()
//    val listOf = listOf("剪切", "删除", "重命名", "文件信息")
    MyDropdownMenu(
        listOf,
        Modifier
            .fillMaxHeight()
            .wrapContentSize(Alignment.Center),
        {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Open Options",
                tint = Color(0xFF85BCFF),
                modifier = Modifier
                    .fillMaxSize()
            )
        }, onClick
    )
}

@Composable
fun AppTopBarDropdownMenu(onClick: (String, Int) -> Unit) {
    val listOf = stringArrayResource(id = R.array.appBarMenu).toList()
//    val listOf = listOf("按文件名称排序", "按创建时间排序", "按修改时间排序","刷新")

    MyDropdownMenu(
        listOf,
        Modifier.wrapContentSize(Alignment.TopEnd),
        {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Open Options"
            )
        }, onClick
    )
}

@Preview
@Composable
private fun FileMoreMenuPreview() {
    FileMoreMenu() { _, _ ->

    }
}


@Preview
@Composable
private fun DropdownMenuPreview() {

}