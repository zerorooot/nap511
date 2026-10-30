package github.zerorooot.nap511.viewmodel.module

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.viewmodel.platform.BackgroundScheduler
import github.zerorooot.nap511.viewmodel.platform.FileNavigator
import github.zerorooot.nap511.viewmodel.store.FileUiStateHolder
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

    private val fakeNavigator = object : FileNavigator {
        override suspend fun getFiles(cid: String) {}
        override suspend fun refresh(forceCache: Boolean) {}
        override suspend fun refresh(cid: String, forceCache: Boolean) {}
        override suspend fun updateOrder(order: OrderBean) {}
    }

    @Before
    fun setUp() {
        stateHolder = FileUiStateHolder("nap511")
        mockBatteryOptimizations = true
        scheduledTaskRaw = null
        scheduledForceRecreate = null

        module = FileIntegrationModule(
            stateHolder = stateHolder,
            navigator = fakeNavigator,
            fileRepository = FileRepository(),
            backgroundScheduler = fakeScheduler
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
}
