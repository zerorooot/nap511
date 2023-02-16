package github.zerorooot.nap511.screen

import android.text.Layout.Alignment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.bean.FileBean

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileCellItem(
    fileBean: FileBean,
    index: Int,
    modifier: Modifier,
    itemOnClick: (Int) -> Unit,
    itemOnLongClick: (Int) -> Unit,
    menuOnClick: (String, Int) -> Unit
) {
    val image = fileBean.fileIco
    val name = fileBean.name
    val size = fileBean.sizeString
    val time = fileBean.createTimeString
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 10.dp,
        modifier = modifier
            .padding(1.dp)
            .combinedClickable(
                onClick = {
                    itemOnClick.invoke(index)
                },
                onLongClick = {
                    itemOnLongClick.invoke(index)
                }
            ),
        color = if (fileBean.isSelect) Color.Cyan else MaterialTheme.colorScheme.surface
    ) {
        Card(
            modifier = Modifier
                .padding(4.dp, 4.dp)
                .height(80.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
            ) {
                Image(
                    painter = painterResource(image),
                    contentDescription = "",
                    modifier = Modifier
                        .weight(0.2f)
                        .height(60.dp)
                        .align(androidx.compose.ui.Alignment.CenterVertically)
                )

                Column(
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
//                        .padding(4.dp)
                        .fillMaxHeight()
                        .weight(0.7f)
                ) {
                    MiddleEllipsisText(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(start = 4.dp, top = 9.dp)
                            .fillMaxWidth(),
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 5.dp, top = 9.dp)
                            .fillMaxSize()
                    ) {
                        Text(
                            text = size,
                            style = MaterialTheme.typography.bodyMedium,
//                            modifier = Modifier.weight(0.3f)
//                                .padding(4.dp)
//                                .weight(0.3f)
                        )
                        Text(
                            text = time,
                            style = MaterialTheme.typography.bodyMedium,
//                            modifier = Modifier.fillMaxSize(),
                            modifier = Modifier.weight(0.7f)
//                                .padding(4.dp)

                        )

                    }
                }

                FileMoreMenu() { itemName, _ ->
                    menuOnClick.invoke(itemName, index)
                }

            }

        }


    }
}


@Preview
@Composable
private fun test() {
    val fileBean =
        FileBean(name = "测试文件名", createTimeString = " 2022-12-27 18:23", sizeString = "624 MB")

    FileCellItem(fileBean, 1, Modifier, { i -> true }, { i -> true }, { i, b -> true })

}
