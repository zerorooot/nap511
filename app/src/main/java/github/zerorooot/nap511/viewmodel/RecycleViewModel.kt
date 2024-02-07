package github.zerorooot.nap511.viewmodel

import android.app.Application
import android.database.DatabaseUtils
import android.widget.Toast
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.RecycleBean
import github.zerorooot.nap511.bean.RecycleInfo
import github.zerorooot.nap511.service.FileService
import github.zerorooot.nap511.util.ConfigUtil
import github.zerorooot.nap511.util.DataStoreUtil
//import github.zerorooot.nap511.util.SharedPreferencesUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class RecycleViewModel(private val cookie: String, private val application: Application) :
    ViewModel() {
    lateinit var drawerState: DrawerState
    lateinit var scope: CoroutineScope

    private val _isRefreshing = MutableStateFlow(false)
    var isRefreshing = _isRefreshing.asStateFlow()

    private val _recycleInfo = MutableStateFlow(RecycleInfo())

    var recycleFileList = mutableStateListOf<RecycleBean>()

    private val _isOpenPasswordDialog = MutableStateFlow(false)
    var isOpenPasswordDialog = _isOpenPasswordDialog.asStateFlow()

    private val fileService: FileService by lazy {
        FileService.getInstance(cookie)
    }


    fun getRecycleFileList() {
        if (recycleFileList.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            _isRefreshing.value = true
            _recycleInfo.value = fileService.recycleList()
            val recycleBeanList = _recycleInfo.value.recycleBeanList
            if (recycleBeanList.size != 0) {
                setRecycleBean(recycleBeanList)
                recycleFileList.clear()
                recycleFileList.addAll(recycleBeanList)
            }

            _isRefreshing.value = false
        }
    }

    fun delete(index: Int) {
        val password = DataStoreUtil.getData(ConfigUtil.password, "")
        if (password == "") {
            _isOpenPasswordDialog.value = true
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
                    DataStoreUtil.putData(ConfigUtil.password, password)
                }
                "删除成功"
            } else {
                DataStoreUtil.putData(ConfigUtil.password, "")
                "删除失败，${revert.errorMsg}"
            }
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteAll() {
        val password = DataStoreUtil.getData(ConfigUtil.password, "")
        if (password == "") {
            _isOpenPasswordDialog.value = true
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
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun closeDialog() {
        _isOpenPasswordDialog.value = false
    }

    fun refresh() {
        recycleFileList.clear()
        getRecycleFileList()
    }

    fun openDrawerState() {
        scope.launch {
            drawerState.open()
        }
    }

    private fun setRecycleBean(recycleBeanList: ArrayList<RecycleBean>) {
        recycleBeanList.forEach { recycleBean ->
            recycleBean.modifiedTimeString =
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                    recycleBean.modifiedTime.toLong() * 1000
                )

            recycleBean.fileSizeString = android.text.format.Formatter.formatFileSize(
                application,
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