package github.zerorooot.nap511.viewmodel.platform

import com.google.gson.JsonObject
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * 单元测试专用的内存 FakeSettingsRepository 实现
 * 纯内存存储，彻底脱离 Android Context 与 DataStore 磁盘依赖。
 */
class FakeSettingsRepository(
    initialState: SettingUiState = SettingUiState(isLoaded = true, keepFoldersOnTop = true)
) : SettingsRepository {

    private val _settingUiStateFlow = MutableStateFlow(initialState)
    override val settingUiStateFlow: StateFlow<SettingUiState> = _settingUiStateFlow.asStateFlow()

    private val dataMap = mutableMapOf<String, Any>()

    override fun <T : Any> getDataFlow(key: String, defaultValue: T): Flow<T> = flow {
        @Suppress("UNCHECKED_CAST")
        emit(dataMap[key] as? T ?: defaultValue)
    }

    override suspend fun <T : Any> getDataSuspend(key: String, defaultValue: T): T {
        @Suppress("UNCHECKED_CAST")
        return dataMap[key] as? T ?: defaultValue
    }

    override suspend fun <T : Any> saveData(key: String, newValue: T) {
        dataMap[key] = newValue
    }

    override suspend fun importConfig(jsonObject: JsonObject) {}

    override suspend fun resetConfig() {
        dataMap.clear()
        _settingUiStateFlow.value = SettingUiState(isLoaded = true)
    }

    fun updateUiState(transform: (SettingUiState) -> SettingUiState) {
        _settingUiStateFlow.value = transform(_settingUiStateFlow.value)
    }
}
