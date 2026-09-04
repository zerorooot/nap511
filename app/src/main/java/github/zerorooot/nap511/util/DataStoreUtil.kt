package github.zerorooot.nap511.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object DataStoreUtil {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "Setting")

    private val dataStore: DataStore<Preferences>
        get() = App.instance.applicationContext.dataStore

    /**
     * 根据默认值类型统一推导 DataStore 的 Key
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> getValueKey(key: String, defaultValue: T): Preferences.Key<T> {
        return when (defaultValue) {
            is Int -> intPreferencesKey(key)
            is Long -> longPreferencesKey(key)
            is String -> stringPreferencesKey(key)
            is Boolean -> booleanPreferencesKey(key)
            is Float -> floatPreferencesKey(key)
            is Double -> doublePreferencesKey(key)
            is Set<*> -> stringSetPreferencesKey(key)
            else -> throw IllegalArgumentException("Unsupported DataStore type for key: $key")
        } as Preferences.Key<T>
    }

    // ==================== 1. Compose 响应式 Flow API（推荐） ====================

    /**
     * 获取数据 Flow，用于 Compose 中 collectAsState() 实现 UI 实时联动
     */
    fun <T : Any> getDataFlow(key: String, defaultValue: T): Flow<T> {
        val prefKey = getValueKey(key, defaultValue)
        return dataStore.data.map { preferences ->
            preferences[prefKey] ?: defaultValue
        }
    }

    // ==================== 2. Suspend 协程 API ====================

    /**
     * 异步获取数据
     */
    suspend fun <T : Any> getDataSuspend(key: String, defaultValue: T): T {
        return getDataFlow(key, defaultValue).first()
    }

    /**
     * 异步保存数据
     */
    suspend fun <T : Any> putDataSuspend(key: String, value: T) {
        val prefKey = getValueKey(key, value)
        dataStore.edit { preferences ->
            preferences[prefKey] = value
        }
    }

    /**
     * 异步删除指定数据
     */
    suspend fun removeDataSuspend(key: String, defaultValue: Any) {
        val prefKey = getValueKey(key, defaultValue)
        dataStore.edit { preferences ->
            preferences.remove(prefKey)
        }
    }

    /**
     * 异步清空所有数据
     */
    suspend fun clearData() {
        dataStore.edit { it.clear() }
    }
}
