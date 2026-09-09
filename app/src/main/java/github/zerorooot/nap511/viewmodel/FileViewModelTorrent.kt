package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.UserSessionManager
import github.zerorooot.nap511.util.onFailureToastAndLog
import kotlinx.coroutines.launch


internal fun FileViewModel.getTorrentTask(sha1: String) {
    torrentBean = TorrentFileBean()
    // 命中缓存的情况
    if (torrentBeanCache.contains(sha1)) {
        torrentBean = torrentBeanCache[sha1]!!
        openCreateSelectTorrentFileDialog()
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
            torrentBean = torrentTask

            // 数据准备完毕后打开对话框并关闭 Loading
            openCreateSelectTorrentFileDialog()
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