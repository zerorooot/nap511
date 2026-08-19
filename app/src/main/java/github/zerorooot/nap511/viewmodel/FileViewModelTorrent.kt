package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.TorrentFileBean
import github.zerorooot.nap511.util.App
import kotlinx.coroutines.launch


internal fun FileViewModel.getTorrentTask(sha1: String) {
    //clear torrent bean
    torrentBean = TorrentFileBean()
    if (torrentBeanCache.contains(sha1)) {
        torrentBean = torrentBeanCache[sha1]!!
        return
    }
    viewModelScope.launch {
        val sign = fileRepository.getOfflineSign().sign
        val torrentTask = try {
            fileRepository.getOfflineTorrentTaskList(sha1, sign, App.uid)
        } catch (e: Exception) {
            XLog.e("getTorrentTask torrentTask error", e)
            TorrentFileBean()
        }
        XLog.d("getTorrentTask torrentTask $torrentTask")
        if (!torrentTask.state) {
            App.instance.toast(torrentTask.errorMessage)
            _isRefreshing.value = false
            return@launch
        }
        torrentTask.fileSizeString = android.text.format.Formatter.formatFileSize(
            App.instance, torrentTask.fileSize
        ) + " "
//            torrentTask.torrentFileListWeb.removeIf { i -> i.wanted == -1 }
        torrentTask.torrentFileListWeb.forEach { b ->
            b.sizeString = android.text.format.Formatter.formatFileSize(
                App.instance, b.size
            ) + " "
        }
        torrentBeanCache[sha1] = torrentTask
        //1是选中的，0的未选中的，-1是_____padding_file_0_如果您看到此文件，请升级到BitComet(比特彗星)0.85或以上版本____
        torrentTask.torrentFileListWeb.removeIf { f -> f.wanted == -1 }

        // test
//            val subList = ArrayList(torrentTask.torrentFileListWeb.subList(0, 2))
//            torrentTask.torrentFileListWeb.clear()
//            torrentTask.torrentFileListWeb = subList

        torrentTask.fileCount = torrentTask.torrentFileListWeb.size
        torrentBean = torrentTask
    }
}

internal fun FileViewModel.addTorrentTask(
    infoHash: String, savePath: String, wanted: String, handle: (Boolean) -> Unit
) {
    viewModelScope.launch {
        val offlineSign = fileRepository.getOfflineSign()
        val sign = offlineSign.sign
        val addTorrentTask = fileRepository.addOfflineTorrentTask(
            infoHash, wanted, savePath, App.uid, sign
        )
        val message = if (addTorrentTask.state) {
            refresh() // 添加成功后刷新列表
            "任务添加成功，文件已保存至 /云下载/${savePath}"
        } else {
            if (addTorrentTask.errorMsg.contains("请验证账号")) {
                handle.invoke(true)
            }
            "任务添加失败，${addTorrentTask.errorMsg}"
        }
        App.instance.toast(message)
    }
}