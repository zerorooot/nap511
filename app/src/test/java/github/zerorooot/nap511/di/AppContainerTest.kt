package github.zerorooot.nap511.di

import android.app.Application
import android.content.Context
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.viewmodel.platform.FakeToastProvider
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppContainerTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var container: DefaultAppContainer

    private class TestApplication(private val filesDirFile: File) : Application() {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = filesDirFile
    }

    @Before
    fun setUp() {
        try {
            XLog.init()
        } catch (_: Throwable) {}
        Dispatchers.setMain(testDispatcher)

        app = TestApplication(tmpFolder.root)
        container = DefaultAppContainer(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDefaultAppContainer_initializesAllDependencies() {
        assertNotNull("toastProvider should not be null", container.toastProvider)
        assertNotNull("fileRepository should not be null", container.fileRepository)
        assertNotNull("subtitleRepository should not be null", container.subtitleRepository)
        assertNotNull("settingsRepository should not be null", container.settingsRepository)
        assertNotNull("authRepository should not be null", container.authRepository)
        assertNotNull("fileService should not be null", container.fileService)
        assertNotNull("repeatService should not be null", container.repeatService)
        assertNotNull("offlineService should not be null", container.offlineService)
        assertNotNull("pagingCoordinator should not be null", container.pagingCoordinator)
        assertNotNull("stringProvider should not be null", container.stringProvider)
        assertNotNull("unzipQueueManager should not be null", container.unzipQueueManager)
    }

    @Test
    fun testDefaultAppContainer_dependenciesAreSingletons() {
        // 多次访问应返回同一实例（by lazy 幂等单例）
        assertSame(container.toastProvider, container.toastProvider)
        assertSame(container.fileRepository, container.fileRepository)
        assertSame(container.subtitleRepository, container.subtitleRepository)
        assertSame(container.settingsRepository, container.settingsRepository)
        assertSame(container.authRepository, container.authRepository)
        assertSame(container.fileService, container.fileService)
        assertSame(container.repeatService, container.repeatService)
        assertSame(container.offlineService, container.offlineService)
        assertSame(container.pagingCoordinator, container.pagingCoordinator)
        assertSame(container.stringProvider, container.stringProvider)
        assertSame(container.unzipQueueManager, container.unzipQueueManager)
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
