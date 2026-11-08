package github.zerorooot.nap511.screenitem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileItemActions
import github.zerorooot.nap511.screen.components.FileMoreMenu
import github.zerorooot.nap511.screen.components.FolderMoreMenu
import github.zerorooot.nap511.screen.components.MenuItemAction

internal const val EXPIRED_IMAGE_TIP = "图片已过期，请刷新"

/**
 * 统一管理文件条目展示名称的响应式状态（供 FileCellItem 与 ImageCellItem 复用）：
 * 以 fileBean.name 与 fileBean.photoThumb 作为联合 Key。
 * 当文件重命名或下拉刷新获取到新的缩略图 URL 时，自动重置为最新文件名。
 */
@Composable
internal fun rememberFileDisplayName(fileBean: FileBean): MutableState<String> =
    remember(fileBean.name, fileBean.photoThumb) {
        mutableStateOf(fileBean.name)
    }

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
    var name by rememberFileDisplayName(fileBean)
    val size = fileBean.sizeString
    val time = fileBean.createTimeString
    val isStar = fileBean.isStar
    val pinned = fileBean.isPinned
    val isVideo = fileBean.isVideo == 1
    val isAudio = fileBean.fileIco == R.drawable.mp3
    val playLong = fileBean.playLongString
    val playProgress = if (fileBean.playLong > 0 && fileBean.currentPlayTime > 0) {
        (fileBean.currentPlayTime.toFloat() / fileBean.playLong.toFloat()).coerceIn(0f, 1f)
    } else 0f

    BaseCellCard(
        modifier = modifier,
        isSelected = fileBean.isSelect,
        isHighlighted = (clickIndex == index) && !fileBean.isSelect,
        onClick = {
            XLog.d("FileCellItem [点击] index=$index, name=${fileBean.name}, isSelect=${fileBean.isSelect}")
            itemActions.onItemClick.invoke(index)
        },
        onLongClick = {
            XLog.d("FileCellItem [长按] index=$index, name=${fileBean.name}")
            itemActions.onItemLongClick.invoke(index)
        },
        leadingContent = {
            FileThumbnail(
                iconRes = image,
                thumbUrl = fileBean.photoThumb,
                cacheKey = fileBean.fileId,
                isTop = pinned,
                isStar = isStar,
                isVideo = isVideo,
                videoDuration = playLong,
                playProgress = playProgress,
                contentDescription = "File Thumbnail",
                onLoadSuccess = {
                    if (name != fileBean.name) {
                        name = fileBean.name
                    }
                    XLog.d("FileCellItem [图片加载成功] index=$index, name=${fileBean.name}")
                },
                onLoadError = {
                    name = EXPIRED_IMAGE_TIP
                    XLog.w("FileCellItem [图片加载失败/过期] index=$index, name=${fileBean.name}")
                }
            )
        },
        titleContent = {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        metadataContent = {
            val metaList = buildList {
                if (isAudio && playLong.isNotEmpty()) {
                    add(playLong.trim())
                } else if (isVideo && fileBean.playLongRatio.isNotEmpty()) {
                    val ratioText = fileBean.playLongRatio.trim()
                    if (ratioText.isNotEmpty()) {
                        add(ratioText)
                    }
                }
                if (size.isNotEmpty()) {
                    add(size)
                }
                if (time.isNotEmpty()) {
                    add(time)
                }
            }
            BaseCellMetadata(
                metaList = metaList
            )
        },
        trailingContent = {
            val dispatchMenuClick: (MenuItemAction, Int) -> Unit = { action, _ ->
                XLog.d("FileCellItem [菜单点击] index=$index, action=$action, name=${fileBean.name}")
                when (action) {
                    MenuItemAction.CUT_FILE -> itemActions.onCut.invoke(index)
                    MenuItemAction.DELETE_FILE -> itemActions.onDelete.invoke(fileBean)
                    MenuItemAction.UNZIP_FILE -> itemActions.onUnzip.invoke(fileBean)
                    MenuItemAction.RENAME_FILE -> itemActions.onRename.invoke(index)
                    MenuItemAction.OFFLINE_CID -> itemActions.onSetOfflineCid.invoke(fileBean)
                    MenuItemAction.FILE_INFO -> itemActions.onFileInfo.invoke(fileBean)
                    MenuItemAction.ARIA2_DOWNLOAD -> itemActions.onAria2Download.invoke(index)
                    MenuItemAction.TOP_FILE, MenuItemAction.UN_TOP_FILE -> itemActions.onTop.invoke(
                        fileBean
                    )
                    MenuItemAction.STAR_FILE, MenuItemAction.UN_STAR_FILE -> itemActions.onStar.invoke(
                        fileBean
                    )
                    MenuItemAction.FORCE_OPEN -> itemActions.onForceOpen.invoke(index)
                    else -> {}
                }
            }

            if (fileBean.isFolder) {
                FolderMoreMenu(
                    isPinned = pinned,
                    isStar = isStar,
                    dispatchMenuClick
                )
            } else {
                FileMoreMenu(
                    isPinned = pinned,
                    isStar = isStar,
                    dispatchMenuClick
                )
            }
        }
    )
}

