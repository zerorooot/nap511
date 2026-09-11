package github.zerorooot.nap511.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import github.zerorooot.nap511.bean.LocationBean
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.util.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingViewModel(
    private val settingsRepository: SettingsRepository = SettingsRepository.getInstance()
) : ViewModel() {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    internal val _currentLocation = MutableStateFlow(LocationBean(0, 0))
    var currentLocation = _currentLocation.asStateFlow()

    fun setLocation(i: Int, i1: Int) {
        _currentLocation.value = LocationBean(i, i1)
    }

    // 从 SettingsRepository 订阅统一暴露的状态 Flow
    val uiState: StateFlow<SettingUiState> = settingsRepository.settingUiStateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000), // 当无界面订阅 5 秒后自动停止收集，节省资源
        initialValue = SettingUiState()
    )

    /**
     * 通用配置保存函数
     */
    fun <T : Any> saveData(key: String, newValue: T) {
        // 过滤出是字符串且小于 0 的数字，命中时弹出提示并中断执行
        (newValue as? String)?.toDoubleOrNull()?.takeIf { it <= 0.0 }?.run {
            App.instance.toast("保存失败，数字不能小于0")
            return
        }

        viewModelScope.launch {
            settingsRepository.saveData(key, newValue)
        }
    }

    /**
     * 导出配置
     */
    fun exportConfig(
        context: Context,
        uri: Uri,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = gson.toJson(uiState.value)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "导出失败") }
            }
        }
    }

    /**
     * 导入配置：通过 SettingsRepository 解析并存入 DataStore
     */
    fun importConfig(
        context: Context,
        uri: Uri,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw Exception("文件无法读取")

                // 解析为 JsonObject
                val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)

                // 通过 Repository 保存
                settingsRepository.importConfig(jsonObject)

                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "解析配置失败") }
            }
        }
    }

    /**
     * 恢复默认设置（保留登录凭证 UID、Cookie、头像信息）
     */
    fun resetConfig(
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                settingsRepository.resetConfig()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "恢复默认设置失败") }
            }
        }
    }
}
