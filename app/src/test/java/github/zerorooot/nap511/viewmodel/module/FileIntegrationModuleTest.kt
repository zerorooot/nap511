package github.zerorooot.nap511.viewmodel.module

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.viewmodel.platform.BackgroundScheduler
import github.zerorooot.nap511.viewmodel.platform.FakeToastProvider
import github.zerorooot.nap511.viewmodel.platform.FileNavigator
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class FileIntegrationModuleTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initLog() {
            XLog.init()
        }
    }

    private lateinit var stateHolder: FileUiStateHolder
    private lateinit var module: FileIntegrationModule
    private val fakeToastProvider = FakeToastProvider()

    private var scheduledTaskRaw: String? = null
    private var scheduledForceRecreate: Boolean? = null
    private var mockBatteryOptimizations: Boolean = true

    private val fakeScheduler = object : BackgroundScheduler {
        override suspend fun handleOfflineTask(currentOfflineTaskRaw: String, forceRecreate: Boolean) {
            scheduledTaskRaw = currentOfflineTaskRaw
            scheduledForceRecreate = forceRecreate
        }

        override fun isIgnoringBatteryOptimizations(): Boolean = mockBatteryOptimizations
    }

    private var lastNavigatedCid: String? = null
    private var lastCopiedText: String? = null

    private val fakeNavigator = object : FileNavigator {
        override fun navigateTo(cid: String) {
            lastNavigatedCid = cid
        }
        override fun refresh(forceCache: Boolean) {}
        override fun refresh(cid: String, forceCache: Boolean) {}
        override fun updateOrder(order: OrderBean) {}
    }

    @Before
    fun setUp() {
        stateHolder = FileUiStateHolder("nap511")
        mockBatteryOptimizations = true
        scheduledTaskRaw = null
        scheduledForceRecreate = null
        lastNavigatedCid = null
        lastCopiedText = null
        fakeToastProvider.clear()

        module = FileIntegrationModule(
            stateHolder = stateHolder,
            navigator = fakeNavigator,
            fileRepository = FileRepository(),
            backgroundScheduler = fakeScheduler,
            onCopyToClipboard = { lastCopiedText = it },
            toastProvider = fakeToastProvider
        )
    }

    @Test
    fun `test refreshBatteryOptimizations reads from background scheduler`() {
        assertTrue(module.isIgnoringBatteryOptimizations.value)

        mockBatteryOptimizations = false
        module.refreshBatteryOptimizations()
        org.junit.Assert.assertFalse(module.isIgnoringBatteryOptimizations.value)
    }

    @Test
    fun `test handleOfflineTask delegates to background scheduler`() = runTest {
        module.handleOfflineTask(currentOfflineTask = "task_abc", forceRecreate = true)

        assertEquals(true, scheduledForceRecreate)
        assertEquals("task_abc", scheduledTaskRaw)
    }

    @Test
    fun `test handleAction JumpDirectory calls navigator navigateTo`() = runTest {
        module.handleAction(github.zerorooot.nap511.viewmodel.platform.DeepLinkAction.JumpDirectory("test_cid_999"))
        assertEquals("test_cid_999", lastNavigatedCid)
    }

    @Test
    fun `test handleAction CopyText calls onCopyToClipboard and emits toast`() = runTest {
        module.handleAction(github.zerorooot.nap511.viewmodel.platform.DeepLinkAction.CopyText("magnet:?xt=urn:btih:123", "复制成功"))
        assertEquals("magnet:?xt=urn:btih:123", lastCopiedText)
        assertTrue(fakeToastProvider.messages.contains("复制成功"))
    }

    @Test
    fun `test handleAction CheckMagnet emits VerifyMagnetLinkAccount route`() = runTest {
        var event: github.zerorooot.nap511.bean.NavEvent? = null
        backgroundScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            module.navigationEvent.collect {
                event = it
            }
        }
        module.handleAction(github.zerorooot.nap511.viewmodel.platform.DeepLinkAction.CheckMagnet)
        testScheduler.runCurrent()
        assertEquals(
            github.zerorooot.nap511.bean.NavEvent.NavigateToScreen(github.zerorooot.nap511.bean.Route.VerifyMagnetLinkAccount),
            event
        )
    }
}
