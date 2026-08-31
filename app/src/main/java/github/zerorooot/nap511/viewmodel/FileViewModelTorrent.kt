package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.onFailureToastAndLog
import kotlinx.coroutines.launch


internal fun FileViewModel.getTorrentTask(sha1: String) {
    torrentBean = TorrentFileBean()
    if (torrentBeanCache.contains(sha1)) {
        torrentBean = torrentBeanCache[sha1]!!
        return
    }
    viewModelScope.launch {
        runCatching {
            val sign = fileRepository.getOfflineSign().sign
            fileRepository.getOfflineTorrentTaskList(sha1, sign, App.uid)
        }.onSuccess { torrentTask ->
            XLog.d("getTorrentTask torrentTask $torrentTask")
            if (!torrentTask.state) {
                App.instance.toast(torrentTask.errorMessage)
                _isRefreshing.value = false
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
            torrentBean = torrentTask
        }.onFailureToastAndLog(tag = "FileViewModelTorrent")
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
                infoHash, wanted, savePath, App.uid, sign
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
        }.onFailureToastAndLog(tag = "FileViewModelTorrent")
    }
}