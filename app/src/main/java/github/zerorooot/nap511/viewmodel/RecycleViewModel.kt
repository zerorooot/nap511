package github.zerorooot.nap511.viewmodel

//import github.zerorooot.nap511.util.SharedPreferencesUtil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.RecycleBean
import github.zerorooot.nap511.bean.RecycleInfo
import github.zerorooot.nap511.repository.DialogEvent
import github.zerorooot.nap511.repository.DialogEventRepository
import github.zerorooot.nap511.service.FileService
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class RecycleViewModel(private val cookie: String) :
    ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    var isRefreshing = _isRefreshing.asStateFlow()

    private val _recycleInfo = MutableStateFlow(RecycleInfo())

    var recycleFileList = mutableStateListOf<RecycleBean>()

    private val dialogEventRepository = DialogEventRepository.getInstance()

    var isOpenRecyclePasswordDialog by mutableStateOf(false)
        private set

    private val fileService: FileService by lazy {
        FileService.getInstance(cookie)
    }

    init {
        viewModelScope.launch {
            dialogEventRepository.events.collect { event ->
                when (event) {
                    is DialogEvent.OpenRecyclePasswordDialog -> isOpenRecyclePasswordDialog = true
                    else -> { /* ignore */
                    }
                }
            }
        }
    }

    fun getRecycleFileList() {
        if (recycleFileList.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                _recycleInfo.value = fileService.recycleList()
                val recycleBeanList = _recycleInfo.value.recycleBeanList
                if (recycleBeanList.isNotEmpty()) {
                    setRecycleBean(recycleBeanList)
                    recycleFileList.clear()
                    recycleFileList.addAll(recycleBeanList)
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
        val password = DataStoreUtil.getData(ConfigKeyUtil.PASSWORD, "")
        if (password == "") {
            isOpenRecyclePasswordDialog = true
            return
        }
        delete(index, password)
    }

    fun delete(index: Int, password: String, save: Boolean = false) {
        viewModelScope.launch {
            val revert = fileService.recycleClean(recycleFileList[index].id, password)
            val message = if (revert.state) {
                recycleFileList.removeAt(index)
                if (save) {
                    DataStoreUtil.putData(ConfigKeyUtil.PASSWORD, password)
                }
                "删除成功"
            } else {
                DataStoreUtil.putData(ConfigKeyUtil.PASSWORD, "")
                "删除失败，${revert.errorMsg}"
            }
            App.instance.toast(message)
        }
    }

    fun deleteAll() {
        val password = DataStoreUtil.getData(ConfigKeyUtil.PASSWORD, "")
        if (password == "") {
            isOpenRecyclePasswordDialog = true
            return
        }
        viewModelScope.launch {
            val recycleCleanAll = fileService.recycleCleanAll(password)
            val message = if (recycleCleanAll.state) {
                recycleFileList.clear()
                "清除成功"
            } else {
                "清除失败，${recycleCleanAll.errorMsg}"
            }
            App.instance.toast(message)
        }
    }

    fun revert(index: Int) {
        viewModelScope.launch {
            val revert = fileService.revert(recycleFileList[index].id)
            val message = if (revert.state) {
                recycleFileList.removeAt(index)
                "恢复成功"
            } else {
                "恢复失败，${revert.errorMsg}"
            }
            App.instance.toast(message)
        }
    }

    fun closeDialog() {
        isOpenRecyclePasswordDialog = false
    }

    fun refresh() {
        recycleFileList.clear()
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
            "zip" -> recycleBean.fileIco = R.drawable.zip
            "7z" -> recycleBean.fileIco = R.drawable.zip
            "rar" -> recycleBean.fileIco = R.drawable.zip
            "png" -> recycleBean.fileIco = R.drawable.png
            "jpg" -> recycleBean.fileIco = R.drawable.png
            "mp3" -> recycleBean.fileIco = R.drawable.mp3
            "txt" -> recycleBean.fileIco = R.drawable.txt
        }
    }
}