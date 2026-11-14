package github.zerorooot.nap511.ui.screen.file

import android.os.SystemClock
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import github.zerorooot.nap511.R
import github.zerorooot.nap511.data.model.FileBean
import github.zerorooot.nap511.ui.model.SettingUiState
import github.zerorooot.nap511.data.model.uniqueId
import github.zerorooot.nap511.ui.navigation.FileOpener
import github.zerorooot.nap511.util.asScrollState
import github.zerorooot.nap511.ui.viewmodel.FileViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 文件列表点击与手势事件协调器
 * 负责处理列表点击防抖、多选态分发、滚动位置持久化及顶底栏显隐联动，
 * 底层文件类型的具体打开动作全部委托给 [FileOpener] 执行。
 */
class FileClickHandler(
    private val fileViewModel: FileViewModel,
    private val fileOpener: FileOpener,
    private val settingUiState: SettingUiState,
    private val coroutineScope: CoroutineScope,
    private val isPreviewActive: Boolean,
    private val isAutoImagePreview: Boolean,
    private val isExpandedScreen: Boolean,
    private val staggeredGrid: LazyStaggeredGridState,
    private val gridState: LazyGridState,
    private val listState: LazyListState,
    private val onTopBarShowChange: (Boolean) -> Unit,
    private val onBottomBarShowChange: (Boolean) -> Unit,
    private val lastClickTime: LongArray
) {
    fun handleFolderClick(fileBean: FileBean) {
        onBottomBarShowChange(true)
        // 关键对齐：清空旧列表前同步提取相邻文件夹 CID
        val neighborFolderCids = if (settingUiState.earlyLoading) {
            val currentList = fileViewModel.fileBeanList
            val i = currentList.indexOfFirst { it.uniqueId == fileBean.uniqueId }
            if (i >= 0) {
                listOf(i - 1, i + 1)
                    .mapNotNull { currentList.getOrNull(it) }
                    .filter { it.isFolder }
                    .map { it.categoryId }
            } else emptyList()
        } else emptyList()

        // 发起乐观导航（立刻更新面包屑、清空旧列表、呈现骨架屏）
        fileViewModel.navigateTo(cid = fileBean.categoryId, targetName = fileBean.name)

        // 关键对齐：轻量延时 400ms 避开骨架屏首波网络高峰，再在后台预热
        if (neighborFolderCids.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                delay(400L.milliseconds)
                neighborFolderCids.forEach { cid ->
                    fileViewModel.updateFileCache(cid)
                }
            }
        }
    }

    fun handleVideoClick(fileBean: FileBean, isForceOpen: Boolean = false) {
        coroutineScope.launch {
            fileOpener.openVideo(fileBean, fileViewModel.fileBeanList, isForceOpen)
            fileViewModel.setRefreshingStatus(false)
        }
    }

    fun handleAudioClick(fileBean: FileBean) {
        onBottomBarShowChange(true)
        fileOpener.openAudio(fileBean, fileViewModel.fileBeanList, navigateToDetail = false)
        fileViewModel.setRefreshingStatus(false)
    }

    fun handlePhotoClick(fileBean: FileBean, isForceOpen: Boolean = false) {
        fileOpener.openPhoto(fileBean, fileViewModel.fileBeanList, isForceOpen)
        if (!isAutoImagePreview) {
            onTopBarShowChange(true)
            onBottomBarShowChange(true)
        }
        fileViewModel.setRefreshingStatus(false)
    }

    fun handleTorrentClick(fileBean: FileBean) {
        fileOpener.openTorrent(fileBean)
    }

    fun handleZipClick(fileBean: FileBean) {
        fileOpener.openZip(fileBean)
    }


    fun handleTextClick(fileBean: FileBean, isForceOpen: Boolean = false) {
        coroutineScope.launch {
            fileOpener.openText(fileBean, isForceOpen)
            fileViewModel.setRefreshingStatus(false)
        }
    }

    fun handleWebClick(fileBean: FileBean) {
        coroutineScope.launch {
            fileOpener.openWeb(fileBean)
            fileViewModel.setRefreshingStatus(false)
        }
    }

    fun myItemOnClick(fileBean: FileBean) {
        if (fileViewModel.isLongClickState) {
            fileViewModel.select(fileBean)
        } else {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastClickTime[0] < 200L) {
                return
            }
            // 关键对齐：文件夹点击绝不提前开启 setRefreshingStatus(true)，由骨架屏独立承载加载态
            if (!fileBean.isFolder) {
                fileViewModel.setRefreshingStatus(true)
            }
            val state = when {
                isPreviewActive -> staggeredGrid.asScrollState
                isExpandedScreen -> gridState.asScrollState
                else -> listState.asScrollState
            }
            fileViewModel.setListLocationAndClickCache(fileBean, state)

            when {
                fileBean.isFolder -> handleFolderClick(fileBean)
                fileBean.isVideo == 1 -> handleVideoClick(fileBean)
                fileBean.fileIco == R.drawable.torrent -> handleTorrentClick(fileBean)
                fileBean.fileIco == R.drawable.zip -> handleZipClick(fileBean)
                fileBean.fileIco == R.drawable.txt -> handleTextClick(fileBean)
                fileBean.fileIco == R.drawable.web -> handleWebClick(fileBean)
                fileBean.fileIco == R.drawable.mp3 -> handleAudioClick(fileBean)
                fileBean.photoThumb.isNotEmpty() -> handlePhotoClick(fileBean)
                else -> fileViewModel.setRefreshingStatus(false)
            }
        }
    }
}
