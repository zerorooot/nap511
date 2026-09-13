package github.zerorooot.nap511.screen

import android.os.SystemClock
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.downloadText
import github.zerorooot.nap511.viewmodel.downloadWeb
import github.zerorooot.nap511.viewmodel.getTorrentTask
import github.zerorooot.nap511.viewmodel.getVideoInfo
import github.zerorooot.nap511.viewmodel.getZipListFile

class FileClickHandler(
    private val fileViewModel: FileViewModel,
    private val audioViewModel: AudioViewModel,
    private val settingUiState: SettingUiState,
    private val onNav: (Route) -> Unit,
    private val isPreviewActive: Boolean,
    private val isExpandedScreen: Boolean,
    private val staggeredGrid: LazyStaggeredGridState,
    private val gridState: LazyGridState,
    private val listState: LazyListState,
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

    fun handleVideoClick(i: Int, fileBean: FileBean) {
        audioViewModel.pause()
        fileViewModel.getVideoInfo(fileBean.pickCode, i, fileBean.name)
    }

    fun handleAudioClick(fileBean: FileBean) {
        onBottomBarShowChange(true)
        fileViewModel.setRefreshingStatus(false)
        audioViewModel.playAudio(fileBean)
    }

    fun handlePhotoClick(fileBean: FileBean) {
        audioViewModel.pause()
        val photoList = fileViewModel.fileBeanList.filter { it.photoThumb != "" }
        if (photoList.isEmpty()) {
            App.instance.toast("图片打开失败，找不到图片url！")
        } else {
            fileViewModel.photoFileBeanList.clear()
            fileViewModel.photoFileBeanList.addAll(photoList)
            fileViewModel.photoIndexOf = photoList.indexOf(fileBean)
            onNav(Route.Photo)
        }
        fileViewModel.setRefreshingStatus(false)
    }

    fun handleTorrentClick(fileBean: FileBean) {
        fileViewModel.getTorrentTask(fileBean.sha1)
    }

    fun handleZipClick(i: Int) {
        fileViewModel.selectIndex = i
        fileViewModel.getZipListFile()
    }

    private fun checkAndDownloadFile(i: Int, fileBean: FileBean, action: () -> Unit) {
        val txtSize = settingUiState.txtSize.toIntOrNull() ?: 200
        if (fileBean.size.toLong() < txtSize * 1024) {
            fileViewModel.selectIndex = i
            action()
        } else {
            fileViewModel.setRefreshingStatus(false)
            App.instance.toast("仅支持打开${txtSize}kb以下的文件")
        }
    }

    fun handleTextClick(i: Int, fileBean: FileBean) {
        checkAndDownloadFile(i, fileBean) {
            fileViewModel.downloadText(fileBean, onNav)
        }
    }

    fun handleWebClick(i: Int, fileBean: FileBean) {
        checkAndDownloadFile(i, fileBean) {
            fileViewModel.downloadWeb(fileBean, onNav)
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
                fileBean.isVideo == 1 -> handleVideoClick(i, fileBean)
                fileBean.fileIco == R.drawable.torrent -> handleTorrentClick(fileBean)
                fileBean.fileIco == R.drawable.zip -> handleZipClick(i)
                fileBean.fileIco == R.drawable.txt -> handleTextClick(i, fileBean)
                fileBean.fileIco == R.drawable.web -> handleWebClick(i, fileBean)
                fileBean.fileIco == R.drawable.mp3 -> handleAudioClick(fileBean)
                fileBean.photoThumb.isNotEmpty() -> handlePhotoClick(fileBean)
                else -> fileViewModel.setRefreshingStatus(false)
            }
        }
    }
}
