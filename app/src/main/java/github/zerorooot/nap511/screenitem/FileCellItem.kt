package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.screen.FileMoreMenu
import kotlin.collections.listOf
import kotlin.math.ceil

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileCellItem(
    fileBean: FileBean,
    index: Int,
    clickIndex: Int = -1,
    //删除会有动画
    modifier: Modifier,
    itemOnClick: (Int) -> Unit,
    itemOnLongClick: (Int) -> Unit,
    onCut: ((Int) -> Unit)? = null,
    onDelete: ((Int) -> Unit)? = null,
    onRename: ((Int) -> Unit)? = null,
    onFileInfo: ((Int) -> Unit)? = null,
    onAria2Download: ((Int) -> Unit)? = null,
) {

    val image = fileBean.fileIco
    val name = fileBean.name
    val size = fileBean.sizeString
    val time = fileBean.createTimeString
    val playLong = fileBean.playLongString
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
                containerColor = if ((clickIndex == index) && !fileBean.isSelect) Color.LightGray else Color.Transparent
            ),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
            ) {
//                Image(
//                    painter = painterResource(image),
//                    contentDescription = "",
//                    modifier = Modifier
//                        .weight(0.2f)
//                        .height(60.dp)
//                        .align(androidx.compose.ui.Alignment.CenterVertically)
//                )
                Box(
                    Modifier
                        .height(60.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Image(
                        painter = if (fileBean.photoThumb == "") painterResource(image) else (
                                rememberAsyncImagePainter(
                                    ImageRequest.Builder(LocalContext.current)
                                        .data(data = fileBean.photoThumb)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .networkCachePolicy(CachePolicy.ENABLED)
                                        .apply(block = fun ImageRequest.Builder.() {
                                            scale(coil.size.Scale.FILL)
                                            placeholder(image)
                                        }).build()
                                )
                                ),
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
//                        .padding(4.dp)
                        .fillMaxHeight()
                        .weight(0.7f)
                ) {
//                    MiddleEllipsisText(
//                        text = name,
//                        style = MaterialTheme.typography.titleMedium,
//                        modifier = Modifier
//                            .padding(start = 4.dp, top = 9.dp)
//                            .fillMaxWidth(),
//                        fontWeight = FontWeight.Bold,
//                    )
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
                        if (fileBean.isVideo == 1) {
                            Text(
                                text = playLong,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = time,
                            style = MaterialTheme.typography.bodyMedium,
//                            modifier = Modifier.fillMaxSize(),
                            modifier = Modifier.weight(0.7f)
//                                .padding(4.dp)

                        )

                    }
                }

                val dispatchMenuClick: (String, Int) -> Unit = { name, _ ->
                    when (name) {
                        "剪切" -> onCut?.invoke(index)
                        "删除" -> onDelete?.invoke(index)
                        "重命名" -> onRename?.invoke(index)
                        "文件信息" -> onFileInfo?.invoke(index)
                        "通过aria2下载" -> onAria2Download?.invoke(index)
                    }
                }
                FileMoreMenu(onClick = dispatchMenuClick)

            }

        }


    }
}

@Composable
fun AutoSizableTextField(
    value: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    maxLines: Int = Int.MAX_VALUE,
    minFontSize: TextUnit,
    scaleFactor: Float = 0.9f,
) {
    BoxWithConstraints(
        modifier = modifier
    ) {
        var nFontSize = fontSize

        // 1. 将 Composable 环境的变量提取到 lambda 外部
        val density = LocalDensity.current
        val fontFamilyResolver = LocalFontFamilyResolver.current

        // 2. 移除 @Composable 注解，直接作为普通 lambda
        val calculateParagraph = {
            Paragraph(
                text = value,
                style = TextStyle(fontSize = nFontSize),
                // 3. 直接使用 BoxWithConstraints 提供的 constraints
                constraints = Constraints(maxWidth = constraints.maxWidth),
                density = density,
                fontFamilyResolver = fontFamilyResolver,
                maxLines = maxLines,
                // 4. 将 ellipsis = false 替换为 overflow = TextOverflow.Clip
                overflow = TextOverflow.Clip
                // 5. 移除了无法推断泛型的 spanStyles 和 placeholders，直接使用底层默认参数
            )
        }

        var intrinsics = calculateParagraph()

        with(density) {
            while ((intrinsics.height.toDp() > maxHeight || intrinsics.didExceedMaxLines) && nFontSize >= minFontSize) {
                nFontSize *= scaleFactor
                intrinsics = calculateParagraph()
            }
        }

        Text(
            text = value,
            style = TextStyle(fontSize = nFontSize),
            maxLines = maxLines,
            fontWeight = FontWeight.Bold,
        )
    }
}

//@Composable
//fun AutoSizableTextField(
//    value: String,
//    modifier: Modifier = Modifier,
//    fontSize: TextUnit = 16.sp,
//    maxLines: Int = Int.MAX_VALUE,
//    minFontSize: TextUnit,
//    scaleFactor: Float = 0.9f,
//) {
//    BoxWithConstraints(
//        modifier = modifier
//    ) {
//        var nFontSize = fontSize
//
//        val calculateParagraph = @Composable {
//            Paragraph(
//                text = value,
//                style = TextStyle(fontSize = nFontSize),
//                constraints = Constraints(
//                    maxWidth = ceil(
//                        with(
//                            LocalDensity.current
//                        ) { maxWidth.toPx() }).toInt()
//                ),
//                density = LocalDensity.current,
//                fontFamilyResolver = LocalFontFamilyResolver.current,
//                spanStyles = listOf(),
//                placeholders = listOf(),
//                maxLines = maxLines,
//                ellipsis = false
//            )
//        }
//
//        var intrinsics = calculateParagraph()
//        with(LocalDensity.current) {
//            while ((intrinsics.height.toDp() > maxHeight || intrinsics.didExceedMaxLines) && nFontSize >= minFontSize) {
//                nFontSize *= scaleFactor
//                intrinsics = calculateParagraph()
//            }
//        }
//
//        Text(
//            text = value,
//            style = TextStyle(fontSize = nFontSize),
//            maxLines = maxLines,
//            fontWeight = FontWeight.Bold,
//        )
//    }
//}

