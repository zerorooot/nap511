package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileItemActions
import github.zerorooot.nap511.screen.components.FileMoreMenu
import github.zerorooot.nap511.screen.components.FolderMoreMenu
import github.zerorooot.nap511.screen.components.MenuItemAction
import github.zerorooot.nap511.util.getCoilCacheUrl

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileCellItem(
    fileBean: FileBean,
    index: Int,
    clickIndex: Int = -1,
    //删除会有动画
    modifier: Modifier,
    itemActions: FileItemActions,
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
                    itemActions.onItemClick.invoke(index)
                },
                onLongClick = {
                    itemActions.onItemLongClick.invoke(index)
                }
            ),
        color = if (fileBean.isSelect) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Card(
            modifier = Modifier
                .padding(4.dp, 4.dp)
                .height(80.dp),
            colors = CardDefaults.cardColors(
                containerColor = if ((clickIndex == index) && !fileBean.isSelect) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent
            ),
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
                    if (fileBean.photoThumb == "") {
                        Image(
                            painter = painterResource(image),
                            modifier = Modifier.size(60.dp), // 用 size 替代 height + width
                            contentScale = ContentScale.Fit,
                            contentDescription = "File Photo",
                        )
                    } else {
                        val context = LocalContext.current
                        val imageLoader = context.imageLoader

                        val imageData = getCoilCacheUrl(imageLoader, fileBean.fileId)
                            ?: fileBean.photoThumb.ifEmpty { image }

                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageData)
                                .memoryCacheKey(fileBean.fileId)
                                .diskCacheKey(fileBean.fileId)
                                .scale(coil.size.Scale.FILL)
                                .placeholder(image)
                                .error(image) // 加载失败时也显示占位图
                                .crossfade(true)
                                .build(),
                            onSuccess = { successState ->
                                XLog.d("FileCellItem [图片加载成功] index=$index, name=${fileBean.name}, imageData=$imageData, source=${successState.result.dataSource}")
                            },
                            contentDescription = "File Thumbnail",
                            modifier = Modifier.size(60.dp), // 用 size 替代 height + width
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                val playLong = fileBean.playLongString
                val playLongRatio = fileBean.playLongRatio
                val isMedia = fileBean.isVideo == 1 || fileBean.fileIco == R.drawable.mp3
                val hasMediaInfo = isMedia && (playLong.isNotEmpty() || playLongRatio.isNotEmpty())
                val hasSubInfo = size.isNotEmpty() || hasMediaInfo

                Column(
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                        .fillMaxHeight()
                        .weight(1f)
                ) {
                    // 1. 文件名（无中间信息时可支持显示 2 行）
                    AutoSizableTextField(
                        value = name,
                        modifier = Modifier.fillMaxWidth(),
                        minFontSize = 10.sp,
                        maxLines = if (hasSubInfo) 1 else 2
                    )
                    // 2. 大小与时长信息（仅在有内容时渲染 Row）
                    if (hasSubInfo) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isMedia) {
                                if (playLong.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    ) {
                                        Text(
                                            text = playLong,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(
                                                horizontal = 2.dp,
                                                vertical = 1.dp
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (playLongRatio.isNotEmpty()) {
                                    Text(
                                        text = playLongRatio,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(
                                            horizontal = 1.dp,
                                            vertical = 1.dp
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    // 3. 修改时间
                    Text(
                        text = size + time,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val dispatchMenuClick: (MenuItemAction, Int) -> Unit = { action, _ ->
                    when (action) {
                        MenuItemAction.CUT_FILE -> itemActions.onCut.invoke(index)
                        MenuItemAction.DELETE_FILE -> itemActions.onDelete.invoke(fileBean)
                        MenuItemAction.UNZIP_FILE -> itemActions.onUnzip.invoke(fileBean)
                        MenuItemAction.RENAME_FILE -> itemActions.onRename.invoke(index)
                        MenuItemAction.FILE_INFO -> itemActions.onFileInfo.invoke(fileBean)
                        MenuItemAction.ARIA2_DOWNLOAD -> itemActions.onAria2Download.invoke(index)
                        MenuItemAction.FORCE_OPEN -> itemActions.onForceOpen.invoke(index)
                        else -> {}
                    }
                }

                if (fileBean.isFolder) {
                    FolderMoreMenu(dispatchMenuClick)
                } else {
                    FileMoreMenu(dispatchMenuClick)
                }


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

