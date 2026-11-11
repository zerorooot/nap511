package github.zerorooot.nap511.di

import android.app.Application
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.viewmodel.platform.FakeToastProvider
import github.zerorooot.nap511.worker.UnzipQueueManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppContainerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var container: DefaultAppContainer

    @Before
    fun setUp() {
        try {
            XLog.init()
        } catch (_: Throwable) {}
        UnzipQueueManager.clear()
        Dispatchers.setMain(testDispatcher)

        SettingsRepository.setInstanceForTesting(github.zerorooot.nap511.viewmodel.platform.FakeSettingsRepository())

        val testApp = App()
        App.instance = testApp
        app = testApp
        container = DefaultAppContainer(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        SettingsRepository.setInstanceForTesting(null)
    }

    @Test
    fun testDefaultAppContainer_initializesAllDependencies() {
        assertNotNull("toastProvider should not be null", container.toastProvider)
        assertNotNull("fileRepository should not be null", container.fileRepository)
        assertNotNull("subtitleRepository should not be null", container.subtitleRepository)
        assertNotNull("settingsRepository should not be null", container.settingsRepository)
        assertNotNull("fileService should not be null", container.fileService)
        assertNotNull("repeatService should not be null", container.repeatService)
        assertNotNull("pagingCoordinator should not be null", container.pagingCoordinator)
        assertNotNull("stringProvider should not be null", container.stringProvider)
    }

    @Test
    fun testDefaultAppContainer_dependenciesAreSingletons() {
        // 多次访问应返回同一实例（by lazy 幂等单例）
        assertSame(container.toastProvider, container.toastProvider)
        assertSame(container.fileRepository, container.fileRepository)
        assertSame(container.subtitleRepository, container.subtitleRepository)
        assertSame(container.settingsRepository, container.settingsRepository)
        assertSame(container.fileService, container.fileService)
        assertSame(container.repeatService, container.repeatService)
        assertSame(container.pagingCoordinator, container.pagingCoordinator)
        assertSame(container.stringProvider, container.stringProvider)
    }

    @Test
    fun testAppToastDelegatesToContainerToastProvider() {
        val fakeToast = FakeToastProvider()
        val testApp = App()
        testApp.container = object : AppContainer by container {
            override val toastProvider = fakeToast
        }

        testApp.toast("测试弹窗消息")

        assertEquals(1, fakeToast.messages.size)
        assertEquals("测试弹窗消息", fakeToast.messages.first())
    }
}
