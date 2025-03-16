package github.zerorooot.nap511.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.preference.PreferenceDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking


object DataStoreUtil {
    // 创建DataStore
    val App.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "Setting"
    )

    // DataStore变量
    private val dataStore = App.instance.dataStore

    /**
     * 存放Int数据
     */
    private suspend fun putIntData(key: String, value: Int) = dataStore.edit {
        it[intPreferencesKey(key)] = value
    }

    /**
     * 存放Set数据
     */
    private suspend fun putStringSetData(key: String, value: Set<*>) = dataStore.edit {
        it[stringSetPreferencesKey(key)] = value as Set<String>
    }

    /**
     * 存放Long数据
     */
    private suspend fun putLongData(key: String, value: Long) = dataStore.edit {
        it[longPreferencesKey(key)] = value
    }

    /**
     * 存放String数据
     */
    private suspend fun putStringData(key: String, value: String) = dataStore.edit {
        it[stringPreferencesKey(key)] = value
    }

    /**
     * 存放Boolean数据
     */
    private suspend fun putBooleanData(key: String, value: Boolean) = dataStore.edit {
        it[booleanPreferencesKey(key)] = value
    }

    /**
     * 存放Float数据
     */
    private suspend fun putFloatData(key: String, value: Float) = dataStore.edit {
        it[floatPreferencesKey(key)] = value
    }

    /**
     * 存放Double数据
     */
    private suspend fun putDoubleData(key: String, value: Double) = dataStore.edit {
        it[doublePreferencesKey(key)] = value
    }

    /**
     * 取出Int数据
     */
    private fun getIntData(key: String, default: Int = 0): Int = runBlocking {
        return@runBlocking dataStore.data.map {
            it[intPreferencesKey(key)] ?: default
        }.first()
    }

    /**
     * 取出Long数据
     */
    private fun getLongData(key: String, default: Long = 0): Long = runBlocking {
        return@runBlocking dataStore.data.map {
            it[longPreferencesKey(key)] ?: default
        }.first()
    }

    /**
     * 取出String数据
     */
    private fun getStringData(key: String, default: String = ""): String = runBlocking {
        return@runBlocking dataStore.data.map {
            it[stringPreferencesKey(key)] ?: default
        }.first()
    }

    /**
     * 取出String set数据
     */
    private fun getStringSetData(key: String): Set<String> =
        runBlocking {
            return@runBlocking dataStore.data.map {
                it[stringSetPreferencesKey(key)] ?: setOf()
            }.first() as Set<String>
        }

    /**
     * 取出Boolean数据
     */
    private fun getBooleanData(key: String, default: Boolean = false): Boolean = runBlocking {
        return@runBlocking dataStore.data.map {
            it[booleanPreferencesKey(key)] ?: default
        }.first()
    }

    /**
     * 取出Float数据
     */
    private fun getFloatData(key: String, default: Float = 0.0f): Float = runBlocking {
        return@runBlocking dataStore.data.map {
            it[floatPreferencesKey(key)] ?: default
        }.first()
    }

    /**
     * 取出Double数据
     */
    private fun getDoubleData(key: String, default: Double = 0.00): Double = runBlocking {
        return@runBlocking dataStore.data.map {
            it[doublePreferencesKey(key)] ?: default
        }.first()
    }

    /**
     * 存数据
     */
    fun <T> putData(key: String, value: T) {
        runBlocking {
            when (value) {
                is Int -> putIntData(key, value)
                is Long -> putLongData(key, value)
                is String -> putStringData(key, value)
                is Boolean -> putBooleanData(key, value)
                is Float -> putFloatData(key, value)
                is Double -> putDoubleData(key, value)
                is Set<*> -> putStringSetData(key, value)
                else -> throw IllegalArgumentException("This type cannot be saved to the Data Store")
            }
        }
    }

    fun <T> getData(key: String, defaultValue: T): T {
        if (defaultValue == null) {
            return getStringData(key, "") as T
        }
        val data = when (defaultValue) {
            is Int -> getIntData(key, defaultValue)
            is Long -> getLongData(key, defaultValue)
            is String -> getStringData(key, defaultValue)
            is Boolean -> getBooleanData(key, defaultValue)
            is Float -> getFloatData(key, defaultValue)
            is Double -> getDoubleData(key, defaultValue)
            is Set<*> -> getStringSetData(key)
            else -> throw IllegalArgumentException("This type cannot be saved to the Data Store key $key defaultValue $defaultValue")
        }
        return data as T
    }

    /**
     * 清空数据
     */
    fun clearData() = runBlocking { dataStore.edit { it.clear() } }
}


class SettingsDataStore() : PreferenceDataStore() {
    override fun putString(key: String?, value: String?) {
        putData(key, value)
    }

    override fun putStringSet(
        key: String?,
        values: Set<String?>?
    ) {
        putData(key, values)
    }

    override fun putInt(key: String?, value: Int) {
        putData(key, value)
    }

    override fun putLong(key: String?, value: Long) {
        putData(key, value)
    }

    override fun putFloat(key: String?, value: Float) {
        putData(key, value)
    }

    override fun putBoolean(key: String?, value: Boolean) {
        putData(key, value)
    }

    override fun getString(key: String?, defValue: String?): String? {
        return getData(key, defValue)
    }

    override fun getStringSet(
        key: String?,
        defValues: Set<String?>?
    ): Set<String?>? {
        return getData(key, defValues)
    }

    override fun getInt(key: String?, defValue: Int): Int {
        return getData(key, defValue)
    }

    override fun getLong(key: String?, defValue: Long): Long {
        return getData(key, defValue)
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        return getData(key, defValue)
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return getData(key, defValue)
    }

    fun <T> putData(key: String?, value: T) {
        if (key == null) {
            throw IllegalArgumentException("put data key couldn't null !!!")
        }
        DataStoreUtil.putData(key, value)
    }

    fun <T> getData(key: String?, defaultValue: T): T {
        if (key == null) {
            throw IllegalArgumentException("get data key couldn't null !!!")
        }
        return DataStoreUtil.getData(key, defaultValue)
    }
}