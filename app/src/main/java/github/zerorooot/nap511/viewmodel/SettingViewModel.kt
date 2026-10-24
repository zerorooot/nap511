package github.zerorooot.nap511.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import github.zerorooot.nap511.bean.LocationBean
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.repository.AuthRepository
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.bean.sync.SyncPayload
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.util.network.UserSessionManager
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
    var selectedCategoryIndex by mutableIntStateOf(0)

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

    /**
     * 清空全部文件缓存（包含内存缓存与磁盘缓存数据）
     * 内部通过 FileCacheManager.clearAll() 执行，并在协程完成后回调 UI 主线程
     */
    fun clearFileCache(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FileCacheManager.clearAll()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "清空文件缓存失败") }
            }
        }
    }

    /**
     * 处理局域网扫码同步接收到的业务载荷（支持模式 1 仅账号凭据 与 模式 2 同步应用设置）
     * 实行前置鉴权预检与事务性安全保护：
     * 若包含 Cookie，在写入前必须先通过 AuthRepository.checkLogin 验证有效性；
     * 验证失败则整体回滚，绝不破坏本地正常会话与已有配置。
     */
    fun handleSettingSyncPayload(payload: SyncPayload) {
        val newCookie = payload.cookie
        val configJson = payload.configJson

        viewModelScope.launch(Dispatchers.IO) {
            // 场景 A：数据中包含账号 Cookie（无论是模式 1 纯凭据，还是模式 2 含凭据的应用配置）
            if (!newCookie.isNullOrBlank()) {
                val cleanCookie = newCookie.replace(" ", "").replace("[\r\n]".toRegex(), "")

                // 1. 调用官方接口检测 Cookie 有效性
                val authResult = AuthRepository.checkLogin(cleanCookie)

                authResult.onSuccess {
                    val targetUid =
                        payload.uid?.ifBlank { UserSessionManager.uid } ?: UserSessionManager.uid

                    val message = if (!configJson.isNullOrBlank()) {
                        // 模式 2：应用设置与账号凭证一并原子更新
                        try {
                            val jsonObject = gson.fromJson(configJson, JsonObject::class.java)
                            settingsRepository.importConfig(jsonObject)
                            "账号凭据有效，应用设置与账号已同步！"
                        } catch (e: Exception) {
                            "配置解析失败: ${e.localizedMessage}"
                        }
                    } else {
                        // 模式 1：仅更新账号凭据与全局会话
                        UserSessionManager.updateSession(cleanCookie, targetUid)
                        "账号凭据有效，账号已同步切换！"
                    }
                    App.instance.toast(message)
                }.onFailure { err ->
                    // 2. 鉴权失败：实行事务性拦截与保护，拒绝改写任何本地配置或会话
                    App.instance.toast("同步失败：账号凭据已过期或验证失败，原配置与账号未受影响 (${err.localizedMessage})")
                }

                return@launch
            }

            // 场景 B：未包含 Cookie 的纯配置同步（极少数未登录设备的纯配置同步）
            if (!configJson.isNullOrBlank()) {
                val message = try {
                    val jsonObject = gson.fromJson(configJson, JsonObject::class.java)
                    settingsRepository.importConfig(jsonObject)
                    "应用配置已同步成功！"
                } catch (e: Exception) {
                    "配置解析失败: ${e.localizedMessage}"
                }
                App.instance.toast(message)
                return@launch
            }
            App.instance.toast("未包含任何可同步的数据")
        }
    }
}
