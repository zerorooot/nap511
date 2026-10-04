package github.zerorooot.nap511.screen.file

import android.os.SystemClock
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.util.FileOpener
import github.zerorooot.nap511.viewmodel.FileViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    fun handleFolderClick(i: Int, fileBean: FileBean) {
        onBottomBarShowChange(true)
        if (settingUiState.earlyLoading) {
            listOf(i - 1, i + 1)
                .mapNotNull { fileViewModel.fileBeanList.getOrNull(it) }
                .filter { it.isFolder }
                .forEach { fileViewModel.updateFileCache(it.categoryId) }
        }
        fileViewModel.getFiles(fileBean.categoryId)
    }

    fun handleVideoClick(fileBean: FileBean) {
        coroutineScope.launch {
            fileOpener.openVideo(fileBean, fileViewModel.fileBeanList)
            fileViewModel.setRefreshingStatus(false)
        }
    }

    fun handleAudioClick(fileBean: FileBean) {
        onBottomBarShowChange(true)
        fileOpener.openAudio(fileBean, fileViewModel.fileBeanList, navigateToDetail = false)
        fileViewModel.setRefreshingStatus(false)
    }

    fun handlePhotoClick(fileBean: FileBean) {
        fileOpener.openPhoto(fileBean, fileViewModel.fileBeanList)
        if (!isAutoImagePreview) {
            onTopBarShowChange(true)
            onBottomBarShowChange(true)
        }
        fileViewModel.setRefreshingStatus(false)
    }

    fun handleTorrentClick(fileBean: FileBean) {
        fileOpener.openTorrent(fileBean)
        fileViewModel.setRefreshingStatus(false)
    }

    fun handleZipClick(fileBean: FileBean) {
        fileOpener.openZip(fileBean)
        fileViewModel.setRefreshingStatus(false)
    }


    fun handleTextClick(fileBean: FileBean) {
        coroutineScope.launch {
            fileOpener.openText(fileBean)
            fileViewModel.setRefreshingStatus(false)
        }
    }

    fun handleWebClick(fileBean: FileBean) {
        coroutineScope.launch {
            fileOpener.openWeb(fileBean)
            fileViewModel.setRefreshingStatus(false)
        }
    }

    fun myItemOnClick(i: Int) {
        if (fileViewModel.isLongClickState) {
            fileViewModel.select(i)
        } else {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastClickTime[0] < 200L) {
                return
            }
            lastClickTime[0] = currentTime

            fileViewModel.setRefreshingStatus(true)

            when {
                isPreviewActive -> fileViewModel.setListLocationAndClickCache(i, staggeredGrid)
                isExpandedScreen -> fileViewModel.setListLocationAndClickCache(i, gridState)
                else -> fileViewModel.setListLocationAndClickCache(i, listState)
            }
            val fileBean = fileViewModel.fileBeanList[i]

            when {
                fileBean.isFolder -> handleFolderClick(i, fileBean)
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
