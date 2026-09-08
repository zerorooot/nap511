package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.RecycleBean
import github.zerorooot.nap511.bean.RecycleInfo
import github.zerorooot.nap511.screen.RecycleUiState
import github.zerorooot.nap511.service.FileService
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.DialogEvent
import github.zerorooot.nap511.util.DialogEventBus
import github.zerorooot.nap511.util.onFailureToastAndLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class RecycleViewModel : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    private val _recycleInfo = MutableStateFlow(RecycleInfo())
    private val _recycleFileList = MutableStateFlow<List<RecycleBean>>(emptyList())
    private val _isOpenRecyclePasswordDialog = MutableStateFlow(false)

    private val dialogEventBus = DialogEventBus.getInstance()

    private val fileService: FileService by lazy {
        FileService.getInstance()
    }

    val uiState: StateFlow<RecycleUiState> = combine(
        _recycleFileList,
        _isRefreshing,
        _isOpenRecyclePasswordDialog
    ) { recycleFileList, isRefreshing, isOpenPasswordDialog ->
        RecycleUiState(
            recycleFileList = recycleFileList,
            isRefreshing = isRefreshing,
            isOpenRecyclePasswordDialog = isOpenPasswordDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecycleUiState()
    )

    fun getRecycleFileList() {
        if (_recycleFileList.value.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                _recycleInfo.value = fileService.recycleList()
                val recycleBeanList = _recycleInfo.value.recycleBeanList
                if (recycleBeanList.isNotEmpty()) {
                    setRecycleBean(recycleBeanList)
                    _recycleFileList.value = recycleBeanList
                }

            } catch (e: NullPointerException) {
                App.instance.toast("获取文件列表失败，建议更新您的Cookie")
            } catch (e: Exception) {
                e.printStackTrace()
                App.instance.toast("${e.message}，请重试～")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun delete(index: Int) {
        viewModelScope.launch {
            val password = DataStoreUtil.getDataSuspend(ConfigKeyUtil.PASSWORD, "")
            if (password == "") {
                _isOpenRecyclePasswordDialog.value = true
                return@launch
            }
            delete(index, password)
        }
    }

    fun delete(index: Int, password: String, save: Boolean = false) {
        viewModelScope.launch {
            runCatching {
                val currentList = _recycleFileList.value
                if (index !in currentList.indices) return@launch
                val item = currentList[index]
                val revert = fileService.recycleClean(item.id, password)
                XLog.i("RecycleViewModel delete $revert")
                val message = if (revert.state) {
                    _recycleFileList.update { list -> list.filterIndexed { i, _ -> i != index } }
                    if (save) {
                        DataStoreUtil.putDataSuspend(ConfigKeyUtil.PASSWORD, password)
                    }
                    "删除成功"
                } else {
                    DataStoreUtil.putDataSuspend(ConfigKeyUtil.PASSWORD, "")
                    "删除失败，${revert.errorMsg}"
                }
                App.instance.toast(message)
            }.onFailureToastAndLog()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            runCatching {
                val password = DataStoreUtil.getDataSuspend(ConfigKeyUtil.PASSWORD, "")
                if (password == "") {
                    _isOpenRecyclePasswordDialog.value = true
                    return@launch
                }
                val recycleCleanAll = fileService.recycleCleanAll(password)
                XLog.i("RecycleViewModel deleteAll $recycleCleanAll")
                val message = if (recycleCleanAll.state) {
                    _recycleFileList.value = emptyList()
                    "清除成功"
                } else {
                    "清除失败，${recycleCleanAll.error}"
                }
                App.instance.toast(message)
            }.onFailureToastAndLog()
        }
    }

    fun revert(index: Int) {
        viewModelScope.launch {
            runCatching {
                val currentList = _recycleFileList.value
                if (index !in currentList.indices) return@launch
                val item = currentList[index]
                val revert = fileService.revert(item.id)
                val message = if (revert.state) {
                    XLog.i("RecycleViewModel revert $revert")
                    val cid = item.cid
                    dialogEventBus.emit(DialogEvent.RefreshFileList(cid))
                    _recycleFileList.update { list -> list.filterIndexed { i, _ -> i != index } }
                    "恢复成功"
                } else {
                    "恢复失败，${revert.error}"
                }
                App.instance.toast(message)
            }.onFailureToastAndLog()
        }
    }

    fun closeDialog() {
        _isOpenRecyclePasswordDialog.value = false
    }

    fun refresh() {
        _recycleFileList.value = emptyList()
        getRecycleFileList()
    }


    private fun setRecycleBean(recycleBeanList: ArrayList<RecycleBean>) {
        recycleBeanList.forEach { recycleBean ->
            recycleBean.modifiedTimeString =
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                    recycleBean.modifiedTime.toLong() * 1000
                )

            recycleBean.fileSizeString = android.text.format.Formatter.formatFileSize(
                App.instance,
                if (recycleBean.fileSize == "") "0".toLong() else recycleBean.fileSize.toLong()
            ) + " "
            setIco(recycleBean)
        }
    }

    private fun setIco(recycleBean: RecycleBean) {
        if (recycleBean.type == "2") {
            recycleBean.fileIco = R.drawable.folder
            return
        }
        if (recycleBean.iv == 1) {
            recycleBean.fileIco = R.drawable.mp4
            return
        }
        when (recycleBean.ico) {
            "apk" -> recycleBean.fileIco = R.drawable.apk
            "iso" -> recycleBean.fileIco = R.drawable.iso
            "torrent" -> recycleBean.fileIco = R.drawable.torrent
            "rar", "tar", "gz", "7z", "zip", "part", "jar" -> recycleBean.fileIco = R.drawable.zip
            "gif", "jpg", "png", "jpeg", "bmp", "tif", "svg", "pic", "heic", "dng", "webp" -> recycleBean.fileIco =
                R.drawable.png

            "doc", "docx", "xls", "pdf", "ppt", "wps", "dps", "et", "mdb", "reg", "txt", "wri", "rtf", "lrc", "vob", "sub", "srt", "ass", "ssa", "idx", "umd", "xlsx", "xlsm", "xltx", "xltm", "xlam", "xlsb", "odt", "pptx", "ods", "odp", "chm", "pot", "pps", "ppsx", "smi", "vtt", "stl", "sbv", "ttml", "ksc", "snc", "krc", "c", "cpp", "h", "asm", "s", "java", "o", "asp", "aspx", "bat", "bas", "prg", "cmd", "log", "php", "js", "go", "sh", "css", "scss", "sass", "less", "class", "hpp", "cc", "hex", "hxx", "cxx", "c++", "cs", "py", "pl", "pm", "md", "cue", "utf", "dpt", "ofd", "eto", "ets", "mhtml", "mht", "uof", "dot", "wpt", "dotx", "docm", "dotm", "ett", "xlt", "pptm", "ppsm", "potx", "potm", "csv", "xml", "html", "htm" ->
                recycleBean.fileIco = R.drawable.txt

            "mp3", "wma", "wav", "midi", "flac", "ram", "ra", "mid", "aac", "m4a", "ape", "au", "ogg", "aif", "aiff", "snd", "voc", "mpa", "cda", "vqf", "wvx", "wmx", "m3u", "m3u8", "ttbl", "ttpl", "tta", "tak", "mpc", "mp+", "mp3pro", "mp1", "mp2", "mac", "xm", "umx", "stm", "s3m", "mtm", "mod", "it", "far", "rmi", "fla", "dts", "dtswav", "awb" -> {
                recycleBean.fileIco = R.drawable.mp3
            }
        }
    }
}