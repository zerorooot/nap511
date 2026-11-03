package github.zerorooot.nap511.util

import androidx.work.Configuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 验证 App 正确实现 androidx.work.Configuration.Provider，
 * 支持按需惰性加载 WorkManager，规避启动阶段 ContentProvider 阻塞。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppWorkManagerConfigTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAppImplementsConfigurationProvider() {
        val app = App()
        assertTrue("App 必须是 Configuration.Provider 实例", (app as Any) is Configuration.Provider)

        val config = app.workManagerConfiguration
        assertNotNull("按需获取的 WorkManager Configuration 不能为空", config)
    }
}
