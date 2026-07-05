package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.ZipBeanList
import github.zerorooot.nap511.bean.ZipStatus
import github.zerorooot.nap511.util.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FileViewModel 的扩展函数：解压相关
 */
internal fun FileViewModel.getZipListFile(
    fileName: String = "", paths: String = "文件", isCheck: Boolean = true
) {
    viewModelScope.launch {
        val fileBean = fileBeanList[selectIndex]
        if (isCheck && !isOpenUnzipDialog && paths == "文件") {
            // 首次打开，调用重构后的状态检查方法
            when (val status = fileRepository.checkZipStatus(fileBean.pickCode)) {
                is ZipStatus.Encrypted -> {
                    XLog.d("${fileBean.name} 是加密压缩包，拦截流程并弹窗")
                    openUnzipPasswordDialog()
                    setRefreshingStatus(false)
                    return@launch // 拦截，等待用户输入密码
                }

                is ZipStatus.UnsupportedOrError -> {
                    XLog.w("业务不支持或发生错误: ${status.message}")
                    // 【核心优化】在这里消费错误，比如弹一个 Toast 提示用户，并终止后续流程
                    App.instance.toast(status.message)
                    setRefreshingStatus(false)
                    return@launch // 拦截，不再继续请求文件列表，避免无意义的崩溃或空白页
                }

                is ZipStatus.Normal -> {
                    // 正常未加密包，不做任何拦截，继续向下执行
                    XLog.d("${fileBean.name} 为普通压缩包，准备直接打开")
                }
            }
        }

        // 只有 ZipStatus.Normal 或者已经处理完流程时，才会走到这里
        unzipBeanList.value = fileRepository.getZipListFile(fileBean.pickCode, fileName, paths)
        openUnzipDialog()
    }
}

internal fun FileViewModel.unzipFile(zipBeanList: ZipBeanList) {
    val refreshCid = currentCid

    viewModelScope.launch {
        val unzipFile = withContext(Dispatchers.IO) {
            val dirs = zipBeanList.list
                .filter { i -> i.fileIco == R.drawable.folder }
                .map { a -> a.fileName }
                .takeIf { it.isNotEmpty() }

            val files = zipBeanList.list
                .filter { i -> i.fileIco != R.drawable.folder }
                .map { a -> a.fileName }
                .takeIf { it.isNotEmpty() }

            val fileBean = fileBeanList[selectIndex]
            val pickCode = fileBean.pickCode
            val unzipFolderName = fileBean.name.substring(0, fileBean.name.length - 4)

            //确定当前目录下是否存在同名文件，如果不存在，则新建一个
            val currentUnzipFolderNameList =
                fileBeanList.filter { i -> i.isFolder && i.name == unzipFolderName }
            var zipFileCid = refreshCid
            if (currentUnzipFolderNameList.isEmpty()) {
                val createFolderMessage = fileRepository.createFolder(
                    refreshCid, unzipFolderName
                )
                XLog.d("fileViewModel.unzipFile $createFolderMessage")
                //(state=false, error=该目录名称已存在。, errno=20004, aid=0, cid=, cname=, fileId=, fileName=)
                //(state=true, error=, errno=, aid=1, cid=123123132131321, cname=cname, fileId=1231232131232, fileName=fileName)
                if (createFolderMessage.state) {
                    zipFileCid = createFolderMessage.cid
                }
            }

            fileRepository.unzipFile(pickCode, zipFileCid, files, dirs, unzipFolderName)
        }

        if (unzipFile.first) {
            refresh(refreshCid)
        } else {
            App.instance.toast(unzipFile.second)
        }
    }
}

internal fun FileViewModel.decryptZip(secret: String) {
    viewModelScope.launch {
        val fileBean = fileBeanList[selectIndex]
        val pickCode = fileBean.pickCode
        closeUnzipPasswordDialog()
        val decryptZip = fileRepository.decryptZip(pickCode, secret)
        if (!decryptZip) {
            App.instance.toast("密码错误～")
            return@launch
        }

        //{"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":4,"progress":100}}}
        if (fileRepository.tryToExtract(pickCode)) {
            getZipListFile(isCheck = false)
        } else {
            App.instance.toast("服务器解压中～")
        }
    }
}
