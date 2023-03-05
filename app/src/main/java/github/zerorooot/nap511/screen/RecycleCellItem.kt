package github.zerorooot.nap511.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import github.zerorooot.nap511.bean.RecycleBean

@Composable
fun RecycleCellItem(
    recycleBean: RecycleBean, //删除会有动画
    modifier: Modifier,
    index: Int,
    menuOnClick: (String, Int) -> Unit
) {
    val image = recycleBean.fileIco
    val name = recycleBean.fileName
    val size = recycleBean.fileSizeString
    val time = recycleBean.modifiedTimeString
    val parentName = recycleBean.parentName
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 10.dp,
        modifier = modifier.padding(1.dp)
    ) {
        Card(
            modifier = Modifier
                .padding(4.dp, 4.dp)
                .height(85.dp),
        ) {
            Row(
                Modifier.fillMaxSize()
            ) {
                Box(
                    Modifier
                        .height(60.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Image(
                        painter = if (recycleBean.photoThumb == "") painterResource(image) else (rememberAsyncImagePainter(
                            ImageRequest.Builder(LocalContext.current)
                                .data(data = recycleBean.photoThumb)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .networkCachePolicy(CachePolicy.ENABLED)
                                .apply(block = fun ImageRequest.Builder.() {
                                    scale(Scale.FILL)
                                    placeholder(image)
                                }).build()
                        )),
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
                    Text(
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        text = parentName,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .fillMaxSize()
                    ) {
                        Text(
                            text = size,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = time,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(0.7f)

                        )

                    }
                }

                RecycleMoreMenu() { itemName, _ ->
                    menuOnClick.invoke(itemName, index)
                }

            }

        }


    }
}
