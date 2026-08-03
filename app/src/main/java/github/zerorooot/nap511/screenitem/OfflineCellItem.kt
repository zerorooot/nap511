package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                )
                        ) {
                            Text(
                                text = percent,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
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