package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.OfflineTask
import github.zerorooot.nap511.screen.OfflineFileMoreMenu


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfflineCellItem(
    offlineTask: OfflineTask,
    index: Int,
    itemOnClick: (Int) -> Unit,
    menuOnClick: (String, Int) -> Unit
) {
    val image = if (offlineTask.fileId == "") R.drawable.other else R.drawable.folder
    val name = offlineTask.name
    val size = offlineTask.sizeString
    val time = offlineTask.timeString
    val percent = offlineTask.percentString
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 10.dp,
        modifier = Modifier
            .padding(1.dp)
            .combinedClickable(
                onClick = {
                    itemOnClick.invoke(index)
                }
            ),
    ) {
        Card(
            modifier = Modifier
                .padding(4.dp, 4.dp)
                .height(80.dp),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
            ) {
                Box(
                    Modifier
                        .height(60.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Image(
                        painter = painterResource(image),
                        modifier = Modifier
                            .height(60.dp)
                            .width(60.dp),
                        contentScale = ContentScale.Fit,
                        contentDescription = "",
                    )
                }

                Column(
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.7f)
                ) {
                    AutoSizableTextField(
                        value = name,
                        modifier = Modifier
                            .padding(start = 4.dp, top = 9.dp)
                            .fillMaxWidth(),
                        minFontSize = 10.sp,
                        maxLines = 2
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 5.dp, top = 9.dp)
                            .fillMaxSize()
                    ) {
                        Text(
                            text = size,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = percent,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Text(
                            text = time,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                OfflineFileMoreMenu() { itemName, _ ->
                    menuOnClick.invoke(itemName, index)
                }
            }

        }


    }
}


@Preview
@Composable
fun p() {
    val offlineTask = OfflineTask(
        name = "test file",
        sizeString = "417.26G",
        percentString = "43%",
        timeString = "2023-02-13 12:43"
    )
    OfflineCellItem(offlineTask, 1, {}, { _, _ -> })

}