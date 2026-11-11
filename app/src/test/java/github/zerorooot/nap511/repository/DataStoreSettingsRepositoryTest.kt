package github.zerorooot.nap511.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.elvishew.xlog.XLog
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import github.zerorooot.nap511.util.ConfigKeyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var testScope: CoroutineScope
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        try {
            XLog.init()
        } catch (_: Throwable) {}

        testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test_setting_${System.nanoTime()}.preferences_pb") }
        )
        repository = DataStoreSettingsRepository(testDataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun testSaveAndGetDataSuspend() = runTest {
        // String
        repository.saveData("test_string_key", "hello_world")
        val stringVal = repository.getDataSuspend("test_string_key", "")
        assertEquals("hello_world", stringVal)

        // Int
        repository.saveData("test_int_key", 42)
        val intVal = repository.getDataSuspend("test_int_key", 0)
        assertEquals(42, intVal)

        // Boolean
        repository.saveData("test_bool_key", true)
        val boolVal = repository.getDataSuspend("test_bool_key", false)
        assertTrue(boolVal)

        // Long
        repository.saveData("test_long_key", 1000L)
        val longVal = repository.getDataSuspend("test_long_key", 0L)
        assertEquals(1000L, longVal)

        // Set
        val testSet = setOf("a", "b", "c")
        repository.saveData("test_set_key", testSet)
        val setVal = repository.getDataSuspend("test_set_key", emptySet<String>())
        assertEquals(testSet, setVal)
    }

    @Test
    fun testGetDataFlowReturnsDefaultWhenNotPresent() = runTest {
        val flowVal = repository.getDataFlow("non_existent_key", "default_val").first()
        assertEquals("default_val", flowVal)
    }

    @Test
    fun testSettingUiStateFlow() = runTest {
        // Initially isLoaded will transition to true upon reading from disk
        val initial = repository.settingUiStateFlow.first { it.isLoaded }
        assertTrue(initial.isLoaded)

        // Update a config
        repository.saveData(ConfigKeyUtil.UID, "test_uid_999")
        val updated = repository.settingUiStateFlow.first { it.uid == "test_uid_999" }
        assertEquals("test_uid_999", updated.uid)

        // Update keepFoldersOnTop
        repository.saveData(ConfigKeyUtil.KEEP_FOLDERS_ON_TOP, false)
        val updatedKeepFolders = repository.settingUiStateFlow.first { !it.keepFoldersOnTop }
        assertFalse(updatedKeepFolders.keepFoldersOnTop)
    }

    @Test
    fun testResetConfigPreservesCredentials() = runTest {
        // Setup initial configuration
        repository.saveData(ConfigKeyUtil.UID, "persisted_uid")
        repository.saveData(ConfigKeyUtil.COOKIE, "persisted_cookie")
        repository.saveData(ConfigKeyUtil.AVATAR_BEAN, "persisted_avatar")
        repository.saveData(ConfigKeyUtil.PASSWORD, "persisted_password")
        repository.saveData(ConfigKeyUtil.ARIA2_URL, "http://localhost:6800/jsonrpc")
        repository.saveData(ConfigKeyUtil.AUTO_ROTATE, true)

        // Reset
        repository.resetConfig()

        // Credentials should remain
        assertEquals("persisted_uid", repository.getDataSuspend(ConfigKeyUtil.UID, ""))
        assertEquals("persisted_cookie", repository.getDataSuspend(ConfigKeyUtil.COOKIE, ""))
        assertEquals("persisted_avatar", repository.getDataSuspend(ConfigKeyUtil.AVATAR_BEAN, ""))
        assertEquals("persisted_password", repository.getDataSuspend(ConfigKeyUtil.PASSWORD, ""))

        // Non-credentials should be cleared to default
        assertEquals("", repository.getDataSuspend(ConfigKeyUtil.ARIA2_URL, ""))
        assertFalse(repository.getDataSuspend(ConfigKeyUtil.AUTO_ROTATE, false))
    }

    @Test
    fun testImportConfig() = runTest {
        val jsonObject = JsonObject().apply {
            add("test_str", JsonPrimitive("test_value"))
            add("test_bool", JsonPrimitive(true))
            add("test_num", JsonPrimitive(123))
            add(ConfigKeyUtil.UID, JsonPrimitive("imported_uid"))
            add(ConfigKeyUtil.COOKIE, JsonPrimitive("imported_cookie"))
        }

        repository.importConfig(jsonObject)

        assertEquals("test_value", repository.getDataSuspend("test_str", ""))
        assertTrue(repository.getDataSuspend("test_bool", false))
        assertEquals(123, repository.getDataSuspend("test_num", 0))
        assertEquals("imported_uid", repository.getDataSuspend(ConfigKeyUtil.UID, ""))
        assertEquals("imported_cookie", repository.getDataSuspend(ConfigKeyUtil.COOKIE, ""))
    }
}
