package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.ZipStatus
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.onFailureToastAndLog
import github.zerorooot.nap511.util.resolveCallerTag
import github.zerorooot.nap511.util.toUserFriendlyMessage
import github.zerorooot.nap511.worker.UnzipQueueManager
import github.zerorooot.nap511.worker.UnzipTaskItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FileViewModel 的扩展函数：解压相关
 */
internal fun FileViewModel.getZipListFile(
    fileName: String = "", paths: String = "文件", isCheck: Boolean = true
) {
    viewModelScope.launch {
        val fileBean = fileBeanList[selectIndex]
        if (isCheck && paths == "文件") {
            var isInterrupted = false
            runCatching {
                fileRepository.checkZipStatus(fileBean.pickCode)
            }.onSuccess { status ->
                when (status) {
                    is ZipStatus.Encrypted -> {
                        XLog.i("${fileBean.name} 是加密压缩包，拦截流程并弹窗")
                        openUnzipPasswordDialog()
                        setRefreshingStatus(false)
                        isInterrupted = true
                    }

                    is ZipStatus.UnsupportedOrError -> {
                        XLog.w("业务不支持或发生错误: ${status.message}")
                        App.instance.toast(status.message)
                        setRefreshingStatus(false)
                        isInterrupted = true
                    }

                    is ZipStatus.Normal -> {
                        XLog.d("${fileBean.name} 为普通压缩包，准备直接打开")
                    }

                    is ZipStatus.Loading -> {
                        val message = "正在进行云解压，请稍等...(${status.progress}%)"
                        XLog.i("${fileBean.name} 要云解压，$message")
                        App.instance.toast(message)
                        setRefreshingStatus(false)
                        isInterrupted = true
                    }
                }
            }.onFailureToastAndLog()

            if (isInterrupted) return@launch
        }

        runCatching {
            fileRepository.getZipListFile(fileBean.pickCode, fileName, paths)
        }.onSuccess { zipList ->
            unzipBeanList.value = zipList
            openUnzipDialog()
        }.onFailure { e ->
            setRefreshingStatus(false) // 失败时也确保关闭
            val logTag = resolveCallerTag()
            val userMsg = e.toUserFriendlyMessage()
            XLog.e("[$logTag] $userMsg", e)
            App.instance.toast(userMsg)
        }
    }
}

internal fun FileViewModel.unzipFile(fileBean: FileBean) {
    viewModelScope.launch(Dispatchers.IO) {
        val cid = currentCid
        val arrayListOf = arrayListOf(fileBean)
        unzipFile(arrayListOf, cid)
    }
}


internal fun FileViewModel.unzipFile(fileBeansList: List<FileBean>, cid: String, pwd: String = "") {
    viewModelScope.launch(Dispatchers.IO) {
        val errorCid = settingUiState.moveFailFile.takeIf { it.isNotEmpty() }?.let { data ->
            fileBeanList.firstOrNull { it.isFolder && it.name == data }?.categoryId
        }

        val taskItems = fileBeansList.map { file ->
            UnzipTaskItem(
                fileBean = file,
                targetCid = cid,
                password = pwd.takeIf { it.isNotEmpty() },
                errorCid = errorCid
            )
        }

        // 统一入队并触发 Worker（KEEP 策略保证单 Worker 循环消费动态队列）
        UnzipQueueManager.enqueueAndStartWorker(taskItems, context)
    }
}

internal fun FileViewModel.decryptZip(secret: String) {
    viewModelScope.launch {
        val fileBean = fileBeanList[selectIndex]
        val pickCode = fileBean.pickCode
        closeUnzipPasswordDialog()
        runCatching {
            val decryptZip = fileRepository.decryptZip(pickCode, secret)
            if (!decryptZip) {
                App.instance.toast("密码错误～")
                return@launch
            }

            if (fileRepository.tryToExtract(pickCode)) {
                getZipListFile(isCheck = true)
            } else {
                App.instance.toast("服务器解压中～")
            }
        }.onFailureToastAndLog()
    }
}
