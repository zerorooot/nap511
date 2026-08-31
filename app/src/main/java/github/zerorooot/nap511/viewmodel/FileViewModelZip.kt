package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.elvishew.xlog.XLog
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.ZipStatus
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.onFailureToastAndLog
import github.zerorooot.nap511.worker.UnzipAllFileWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

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
                        XLog.d("${fileBean.name} 是加密压缩包，拦截流程并弹窗")
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
                        XLog.d("${fileBean.name} 要云解压，$message")
                        App.instance.toast(message)
                        setRefreshingStatus(false)
                        isInterrupted = true
                    }
                }
            }.onFailureToastAndLog(tag = "FileViewModelZip")

            if (isInterrupted) return@launch
        }

        runCatching {
            fileRepository.getZipListFile(fileBean.pickCode, fileName, paths)
        }.onSuccess { zipList ->
            unzipBeanList.value = zipList
            openUnzipDialog()
        }.onFailureToastAndLog(tag = "FileViewModelZip")
    }
}


internal fun FileViewModel.unzipFile() {
    viewModelScope.launch(Dispatchers.IO) {
        val cid = currentCid
        val arrayListOf = arrayListOf(fileBeanList[selectIndex])
        unzipFile(arrayListOf, cid)
    }
}

internal fun FileViewModel.unzipFile(fileBeansList: List<FileBean>, cid: String, pwd: String = "") {
    val listType = object : TypeToken<List<FileBean>>() {}.type
    val listJson = Gson().toJson(fileBeansList, listType)
    val dataBuilder: Data.Builder = Data.Builder()
    //防止输入太多导致崩溃
    val cacheFile =
        File(App.instance.cacheDir, "unzip_tasks_${System.currentTimeMillis()}.json")
    cacheFile.writeText(listJson)
    dataBuilder.putString("listPath", cacheFile.absolutePath)
    dataBuilder.putString("cid", cid)
    if (pwd != "") {
        dataBuilder.putString("pwd", pwd)
    }

    //获取离线失败移动目录cid
    val errorCid = DataStoreUtil.getData(ConfigKeyUtil.MOVE_FAIL_FILE, "")
        .takeIf { it.isNotEmpty() }
        ?.let { data ->
            fileBeanList.firstOrNull { it.isFolder && it.name == data }?.categoryId
        }
        ?.let { errorCid ->
            XLog.d("FileViewModel.unzipFile设置errorCid $errorCid")
            dataBuilder.putString("errorCid", errorCid)
            errorCid
        }


    val request: OneTimeWorkRequest =
        OneTimeWorkRequest.Builder(UnzipAllFileWorker::class.java)
            .addTag("UnzipAllFileWorkerOneTimeWorkRequest")
            .setInputData(dataBuilder.build()).build()

    startUnzipWorker(request, cid, errorCid)
}

internal fun FileViewModel.startUnzipWorker(
    request: OneTimeWorkRequest,
    cid: String,
    errorCid: String?
) {
    val workManager = WorkManager.getInstance(context.applicationContext)
    workManager.enqueueUniqueWork(
        "unzipAllFileWorker", ExistingWorkPolicy.APPEND_OR_REPLACE, request
    )
    viewModelScope.launch(Dispatchers.IO) {
        // 将 LiveData 转为 Flow 或者直接观察（这里利用 WorkManager 提供的 LiveData 转换为 Flow）
        // 注意：需要引入 androidx.lifecycle:lifecycle-livedata-ktx 依赖
        workManager.getWorkInfoByIdLiveData(request.id).asFlow() // 将 LiveData 转换为 Flow
            .collect { workInfo ->
                if (workInfo != null) {
                    if (workInfo.state == WorkInfo.State.SUCCEEDED || workInfo.state == WorkInfo.State.FAILED) {
                        refresh(cid)
                        errorCid?.let {
                            refresh(it)
                        }
                    }
                }
            }
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
        }.onFailureToastAndLog(tag = "FileViewModelZip")
    }
}
