package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileDialogState
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.network.UserSessionManager
import github.zerorooot.nap511.util.onFailureToastAndLog
import kotlinx.coroutines.launch


internal fun FileViewModel.getTorrentTask(sha1: String) {
    updateDialogState { copy(torrentBean = TorrentFileBean()) }
    // 命中缓存的情况
    val cached = torrentBeanCache[sha1]
    if (cached != null) {
        updateDialogState {
            copy(
                torrentBean = cached,
                activeDialog = FileDialogState.CreateSelectTorrentFile
            )
        }
        setRefreshingStatus(false)
        return
    }

    // 网络请求的情况
    viewModelScope.launch {
        runCatching {
            val sign = fileRepository.getOfflineSign().sign
            fileRepository.getOfflineTorrentTaskList(sha1, sign, UserSessionManager.uid)
        }.onSuccess { torrentTask ->
            XLog.d("getTorrentTask torrentTask $torrentTask")
            if (!torrentTask.state) {
                App.instance.toast(torrentTask.errorMessage)
                setRefreshingStatus(false)
                return@onSuccess
            }
            torrentTask.fileSizeString = android.text.format.Formatter.formatFileSize(
                App.instance, torrentTask.fileSize
            ) + " "
            torrentTask.torrentFileListWeb.forEach { b ->
                b.sizeString = android.text.format.Formatter.formatFileSize(
                    App.instance, b.size
                ) + " "
            }
            torrentBeanCache[sha1] = torrentTask
            torrentTask.torrentFileListWeb.removeIf { f -> f.wanted == -1 }
            torrentTask.fileCount = torrentTask.torrentFileListWeb.size
            updateDialogState {
                copy(
                    torrentBean = torrentTask,
                    activeDialog = FileDialogState.CreateSelectTorrentFile
                )
            }
            setRefreshingStatus(false)
        }.onFailure {
            setRefreshingStatus(false)
        }
    }
}

internal fun FileViewModel.addTorrentTask(
    infoHash: String, savePath: String, wanted: String, handle: (Boolean) -> Unit
) {
    viewModelScope.launch {
        runCatching {
            val offlineSign = fileRepository.getOfflineSign()
            val sign = offlineSign.sign
            val addTorrentTask = fileRepository.addOfflineTorrentTask(
                infoHash, wanted, savePath, UserSessionManager.uid, sign
            )
            if (addTorrentTask.state) {
                refresh()
                "任务添加成功，文件已保存至 /云下载/${savePath}"
            } else {
                if (addTorrentTask.errorMsg.contains("请验证账号")) {
                    handle.invoke(true)
                }
                "任务添加失败，${addTorrentTask.errorMsg}"
            }
        }.onSuccess { message ->
            App.instance.toast(message)
        }.onFailureToastAndLog()
    }
}